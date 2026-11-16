package ro.stucanii.aissociation.research;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public class ResearchDtos {
    public record Answers(
            @NotBlank @Pattern(regexp = "Member|Project organizer|Association leader|Participant|Other") String involvement,
            @NotBlank @Pattern(regexp = "1–20|21–50|51–100|101–200|201[+]|Not sure|Not applicable") String memberCount,
            @NotNull @Size(max = 2000) String organization,
            @NotNull @Size(min = 1, max = 6) List<@NotNull @Pattern(regexp = "Open call|Application form|Direct invitation|Appointment|Informal discussion|Other") String> recruitment,
            @NotNull @Size(max = 1000) String recruitmentDetails,
            @NotNull @Size(min = 1, max = 6) List<@NotNull @Pattern(regexp = "Meetings|Spreadsheets|Task management app|Chat messages|No consistent process|Other") String> tasks,
            @NotNull @Size(max = 1000) String taskDetails,
            @NotNull @Size(min = 1, max = 8) List<@NotNull @Pattern(regexp = "Google Workspace|Microsoft 365|WhatsApp|Discord|Notion|Trello or similar|None|Other") String> tools,
            @NotNull @Size(max = 1000) String toolDetails,
            @NotNull @Size(min = 1, max = 3) List<@NotNull @Pattern(regexp = "Finding information|Unclear responsibilities|Following task progress|Recruiting teams|Communication|Registration and attendance|Other") String> challenges,
            @NotNull @Size(max = 1000) String challengeDetails,
            @NotNull @Size(max = 2000) String registration,
            @NotNull @Size(min = 1, max = 3) List<@NotNull @Pattern(regexp = "Tasks|Recruitment|Event publishing|Registrations|Attendance|Communication|Other") String> capabilities,
            @NotNull @Size(max = 1000) String capabilityDetails,
            @NotNull @Size(max = 2000) String barriers,
            @NotNull @Size(max = 2000) String suggestions,
            @NotNull @Size(max = 150) String associationName,
            @NotNull @Email @Size(max = 254) String contactEmail) {}

    public record Submission(@NotNull UUID submissionId, @NotNull @Valid Answers answers) {}
    public record Response(UUID id, Instant submittedAt, Answers answers) {}
}
