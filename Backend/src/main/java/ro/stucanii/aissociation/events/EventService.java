package ro.stucanii.aissociation.events;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.stucanii.aissociation.associations.AssociationAccess;
import ro.stucanii.aissociation.associations.AssociationRole;
import ro.stucanii.aissociation.identity.ApiException;
import ro.stucanii.aissociation.identity.AuthenticatedUser;
import ro.stucanii.aissociation.projects.ProjectAccess;

@Service
public class EventService {
    private static final String SELECT = """
            SELECT e.*, a.name AS association_name, a.slug AS association_slug
            FROM events e JOIN associations a ON a.id = e.association_id
            """;
    private final JdbcClient jdbc;
    private final AssociationAccess associations;
    private final ProjectAccess projects;
    private final Clock clock;
    public EventService(JdbcClient jdbc, AssociationAccess associations, ProjectAccess projects, Clock clock) {
        this.jdbc=jdbc; this.associations=associations; this.projects=projects; this.clock=clock;
    }

    public List<EventDtos.OrganizerEvent> list(UUID association, AuthenticatedUser actor) {
        var role = associations.requireMember(association, actor.id());
        // Only organizers receive private event projections, including drafts.
        return jdbc.sql(SELECT + " WHERE e.association_id = ? ORDER BY e.starts_at, e.id")
                .param(association).query(this::row).list().stream()
                .filter(event -> canManage(association, event.projectId(), actor.id(), role)).toList();
    }

    private boolean canManage(UUID association, UUID project, UUID actor, AssociationRole role) {
        if (project == null) return role == AssociationRole.ADMIN;
        try { projects.requireManager(association, project, actor); return true; }
        catch (ApiException denied) {
            if (denied.status() == HttpStatus.FORBIDDEN) return false;
            throw denied;
        }
    }

    public EventDtos.OrganizerEvent detail(UUID association, UUID id, AuthenticatedUser actor) {
        associations.requireMember(association, actor.id());
        var event = find(association, id);
        authorize(association, event.projectId(), actor);
        return event;
    }

    @Transactional
    public EventDtos.OrganizerEvent create(UUID association, EventDtos.SaveEvent body, AuthenticatedUser actor) {
        lockScope(association, body.projectId());
        authorize(association, body.projectId(), actor);
        validate(body);
        if (!body.startsAt().isAfter(clock.instant())) throw invalid("INVALID_EVENT_DATE", "New events must start in the future.");
        UUID id=UUID.randomUUID();
        var now=Timestamp.from(clock.instant());
        jdbc.sql("""
                INSERT INTO events (id, association_id, project_id, title, description, location,
                  starts_at, ends_at, time_zone, registration_deadline, capacity, status, created_at, updated_at)
                VALUES (:id, :association, :project, :title, :description, :location,
                  :start, :end, :zone, :deadline, :capacity, 'DRAFT', :now, :now)
                """).param("id",id).param("association",association).param("project",body.projectId())
                .param("title",body.title().strip()).param("description",body.description().strip())
                .param("location",body.location().strip()).param("start",Timestamp.from(body.startsAt()))
                .param("end",Timestamp.from(body.endsAt())).param("zone",body.timeZone())
                .param("deadline",Timestamp.from(body.registrationDeadline())).param("capacity",body.capacity())
                .param("now",now).update();
        return find(association,id);
    }

    @Transactional
    public EventDtos.OrganizerEvent edit(UUID association, UUID id, EventDtos.SaveEvent body, AuthenticatedUser actor) {
        var old=locked(association,id,actor);
        if (!Objects.equals(old.projectId(),body.projectId())) throw invalid("EVENT_OWNERSHIP_FIXED", "An event's project cannot be changed.");
        editable(old);
        validate(body);
        // Until Denisa's seat ledger is integrated, never reduce published capacity.
        if (old.event().status().equals("PUBLISHED") && body.capacity()!=null
                && (old.event().capacity()==null || body.capacity()<old.event().capacity())) {
            throw conflict("CAPACITY_REDUCTION_UNAVAILABLE", "Published capacity can only increase or become unlimited.");
        }
        jdbc.sql("""
                UPDATE events SET title=:title, description=:description, location=:location,
                starts_at=:start, ends_at=:end, time_zone=:zone, registration_deadline=:deadline,
                capacity=:capacity, updated_at=:now WHERE id=:id
                """).param("title",body.title().strip()).param("description",body.description().strip())
                .param("location",body.location().strip()).param("start",Timestamp.from(body.startsAt()))
                .param("end",Timestamp.from(body.endsAt())).param("zone",body.timeZone())
                .param("deadline",Timestamp.from(body.registrationDeadline())).param("capacity",body.capacity())
                .param("now",Timestamp.from(clock.instant())).param("id",id).update();
        return find(association,id);
    }

    @Transactional
    public EventDtos.OrganizerEvent transition(UUID association, UUID id, String action, AuthenticatedUser actor) {
        var old=locked(association,id,actor);
        String status=old.event().status();
        String next;
        if (action.equals("publish") && status.equals("DRAFT")) {
            if (!old.event().startsAt().isAfter(clock.instant()) || !old.event().registrationDeadline().isAfter(clock.instant()))
                throw invalid("EVENT_WINDOW_CLOSED", "Publishing requires a future start and registration deadline.");
            next="PUBLISHED";
        } else if (action.equals("cancel") && status.equals("PUBLISHED")) next="CANCELLED";
        else if (action.equals("complete") && status.equals("PUBLISHED") && !old.event().endsAt().isAfter(clock.instant())) next="COMPLETED";
        else throw conflict("INVALID_EVENT_TRANSITION", "This event cannot perform that action in its current state.");
        jdbc.sql("UPDATE events SET status=?, updated_at=? WHERE id=?")
                .params(next,Timestamp.from(clock.instant()),id).update();
        return find(association,id);
    }

    public EventDtos.PublicEvent publicDetail(UUID id) {
        return jdbc.sql(SELECT + " WHERE e.id=? AND e.status <> 'DRAFT'").param(id).query(this::row)
                .optional().map(EventDtos.OrganizerEvent::event).orElseThrow(this::missing);
    }

    public EventDtos.Page discover(UUID association, Instant from, Instant to, int page, int size) {
        if (page<0 || page>100000 || size<1 || size>100 || (from!=null && to!=null && !to.isAfter(from)))
            throw invalid("INVALID_EVENT_FILTER", "Use an increasing date range, nonnegative page and size 1–100.");
        String where=" WHERE e.status='PUBLISHED'";
        Map<String,Object> params=new HashMap<>();
        if (association!=null) { where+=" AND e.association_id=:association"; params.put("association",association); }
        if (from!=null) { where+=" AND e.ends_at>:fromDate"; params.put("fromDate",Timestamp.from(from)); }
        if (to!=null) { where+=" AND e.starts_at<:toDate"; params.put("toDate",Timestamp.from(to)); }
        long total=jdbc.sql("SELECT COUNT(*) FROM events e"+where).params(params).query(Long.class).single();
        var items=jdbc.sql(SELECT+where+" ORDER BY e.starts_at, e.id LIMIT :limit OFFSET :offset")
                .params(params).param("limit",size).param("offset",page*size).query(this::row).list()
                .stream().map(EventDtos.OrganizerEvent::event).toList();
        return new EventDtos.Page(items,total,page,size);
    }

    private EventDtos.OrganizerEvent locked(UUID association, UUID id, AuthenticatedUser actor) {
        // Association lock serializes membership changes and event ownership checks.
        lockScope(association,null);
        associations.requireMember(association,actor.id());
        var event=find(association,id);
        if (event.projectId()!=null) lockScope(association,event.projectId());
        jdbc.sql("SELECT id FROM events WHERE id=? FOR UPDATE").param(id).query(UUID.class).single();
        authorize(association,event.projectId(),actor);
        return find(association,id);
    }
    private void lockScope(UUID association, UUID project) {
        if (jdbc.sql("SELECT id FROM associations WHERE id=? FOR UPDATE").param(association).query(UUID.class).optional().isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND,"ASSOCIATION_NOT_FOUND","Association not found.");
        if (project!=null && jdbc.sql("SELECT id FROM projects WHERE id=? AND association_id=? FOR UPDATE")
                .params(project,association).query(UUID.class).optional().isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND,"PROJECT_NOT_FOUND","Project not found in this association.");
    }
    private void authorize(UUID association, UUID project, AuthenticatedUser actor) {
        if (project==null) associations.requireAdmin(association,actor.id());
        else projects.requireManager(association,project,actor.id());
    }
    private void editable(EventDtos.OrganizerEvent event) {
        if (!Set.of("DRAFT","PUBLISHED").contains(event.event().status()))
            throw conflict("EVENT_READ_ONLY","Cancelled and completed events cannot be edited.");
    }
    private void validate(EventDtos.SaveEvent body) {
        if (!ZoneId.getAvailableZoneIds().contains(body.timeZone())) throw invalid("INVALID_TIME_ZONE","Choose a valid IANA timezone.");
        if (!body.endsAt().isAfter(body.startsAt())) throw invalid("INVALID_EVENT_DATE","End must be after start.");
        if (body.registrationDeadline().isAfter(body.startsAt())) throw invalid("INVALID_DEADLINE","Registration deadline must be no later than start.");
    }
    private EventDtos.OrganizerEvent find(UUID association, UUID id) {
        return jdbc.sql(SELECT+" WHERE e.id=? AND e.association_id=?").params(id,association).query(this::row).optional().orElseThrow(this::missing);
    }
    private EventDtos.OrganizerEvent row(ResultSet rs,int n) throws SQLException {
        var association=new EventDtos.Association(rs.getObject("association_id",UUID.class),rs.getString("association_name"),rs.getString("association_slug"));
        var event=new EventDtos.PublicEvent(rs.getObject("id",UUID.class),association,rs.getString("title"),rs.getString("description"),rs.getString("location"),
                rs.getTimestamp("starts_at").toInstant(),rs.getTimestamp("ends_at").toInstant(),rs.getString("time_zone"),
                rs.getTimestamp("registration_deadline").toInstant(),rs.getObject("capacity",Integer.class),rs.getString("status"));
        return new EventDtos.OrganizerEvent(event,rs.getObject("project_id",UUID.class),rs.getTimestamp("updated_at").toInstant());
    }
    private ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND,"EVENT_NOT_FOUND","Event not found."); }
    private ApiException invalid(String code,String message) { return new ApiException(HttpStatus.BAD_REQUEST,code,message); }
    private ApiException conflict(String code,String message) { return new ApiException(HttpStatus.CONFLICT,code,message); }
}
