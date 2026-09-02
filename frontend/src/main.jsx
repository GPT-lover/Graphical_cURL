import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './index.css'

// Standard React 19 entry point: find <div id="root"> and render <App/> into it.
// StrictMode double-invokes some functions in development to surface bugs early;
// it has no effect in a production build.
ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
