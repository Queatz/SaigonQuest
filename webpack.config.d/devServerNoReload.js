// Force-disable Hot Module Replacement and Live Reload to stop periodic reloads
// This runs in the context provided by the Kotlin/JS Gradle plugin where `config` is in scope.

if (!config.devServer) {
  config.devServer = {}
}

// Disable features that trigger browser reloads
config.devServer.hot = false
config.devServer.liveReload = false

// Also disable the client-side overlay to reduce noise
config.devServer.client = {
  ...(config.devServer.client || {}),
  overlay: false,
}

console.log('[webpack.config.d] DevServer overrides: hot=false, liveReload=false, overlay=false')
