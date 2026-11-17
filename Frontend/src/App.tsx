import { IonApp, IonRouterOutlet, setupIonicReact } from '@ionic/react';
import { IonReactRouter } from '@ionic/react-router';
import { Navigate, Route } from 'react-router-dom';

import { AuthProvider } from './auth/AuthProvider';
import PublicAssociationsPage from './discovery/PublicAssociationsPage';
import { EventDetailPage, EventsPage } from './events/EventPages';
import EventWorkspace from './events/EventWorkspace';
import ChatPage from './messaging/ChatPage';
import AccountPage from './pages/AccountPage';
import AuthPage from './pages/AuthPage';
import Home from './pages/Home';
import PlatformAssociationsPage from './pages/PlatformAssociationsPage';
import WorkspacePage from './pages/WorkspacePage';
import ProjectPages from './projects/ProjectPages';
import RosterPage from './registration/RosterPage';
import ResearchResponsesPage from './research/ResearchResponsesPage';
import ProjectTasksPage from './tasks/ProjectTasksPage';

/* Core CSS required for Ionic components to work properly */
import '@ionic/react/css/core.css';

/* Basic CSS for apps built with Ionic */
import '@ionic/react/css/normalize.css';
import '@ionic/react/css/structure.css';
import '@ionic/react/css/typography.css';

/* Optional CSS utils that can be commented out */
import '@ionic/react/css/padding.css';
import '@ionic/react/css/float-elements.css';
import '@ionic/react/css/text-alignment.css';
import '@ionic/react/css/text-transformation.css';
import '@ionic/react/css/flex-utils.css';
import '@ionic/react/css/display.css';

/**
 * Ionic Dark Mode
 * -----------------------------------------------------
 * For more info, please see:
 * https://ionicframework.com/docs/theming/dark-mode
 */

/* import '@ionic/react/css/palettes/dark.always.css'; */
/* import '@ionic/react/css/palettes/dark.class.css'; */
// Brand v1 uses an explicit light palette; dark surfaces are opt-in.
import './theme/brand.css';

/* Theme variables */
import './theme/variables.css';

setupIonicReact();

const App: React.FC = () => (
  <AuthProvider>
    <IonApp>
      <IonReactRouter>
        <IonRouterOutlet>
          <Route path="/home" element={<Home />} />
          <Route path="/events" element={<EventsPage />} />
          <Route path="/events/:eventId" element={<EventDetailPage />} />
          <Route path="/workspaces/:slug/events" element={<EventWorkspace />} />
          <Route path="/workspaces/:slug/events/:eventId/registrations" element={<RosterPage />} />
          <Route path="/associations" element={<PublicAssociationsPage />} />
          <Route path="/associations/:slug" element={<PublicAssociationsPage />} />
          <Route path="/login" element={<AuthPage mode="login" key="login" />} />
          <Route path="/register" element={<AuthPage mode="register" key="register" />} />
          <Route path="/account" element={<AccountPage />} />
          <Route path="/workspaces/:slug" element={<WorkspacePage />} />
          <Route path="/workspaces/:slug/messages" element={<ChatPage />} />
          <Route path="/workspaces/:slug/projects" element={<ProjectPages />} />
          <Route path="/workspaces/:slug/projects/:projectId" element={<ProjectPages />} />
          <Route path="/workspaces/:slug/projects/:projectId/tasks" element={<ProjectTasksPage />} />
          <Route path="/admin/associations" element={<PlatformAssociationsPage />} />
          <Route path="/admin/research" element={<ResearchResponsesPage />} />
          <Route path="/" element={<Navigate to="/home" replace />} />
        </IonRouterOutlet>
      </IonReactRouter>
    </IonApp>
  </AuthProvider>
);

export default App;
