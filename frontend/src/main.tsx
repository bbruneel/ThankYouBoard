import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { Auth0Provider } from '@auth0/auth0-react';
import App from './App.tsx';
import './index.css';

const domain = import.meta.env.VITE_AUTH0_DOMAIN;
const clientId = import.meta.env.VITE_AUTH0_CLIENT_ID;
const audience = import.meta.env.VITE_AUTH0_AUDIENCE;

if (!domain || !clientId) {
  console.warn(
    'Auth0 is not fully configured. Set VITE_AUTH0_DOMAIN and VITE_AUTH0_CLIENT_ID to enable authentication.',
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      {domain && clientId ? (
        <Auth0Provider
          domain={domain}
          clientId={clientId}
          // Persist the Auth0 cache so reloads do not drop the SPA session.
          cacheLocation="localstorage"
          authorizationParams={{
            redirect_uri: window.location.origin,
            audience: audience || undefined,
          }}
        >
          <App />
        </Auth0Provider>
      ) : (
        <App />
      )}
    </BrowserRouter>
  </StrictMode>,
);
