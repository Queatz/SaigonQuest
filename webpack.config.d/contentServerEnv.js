// Inject CONTENT_SERVER_URL for Kotlin/JS via process.env
const webpack = require('webpack');

config.plugins = [
  ...(config.plugins || []),
  new webpack.DefinePlugin({
    'process.env.API_SERVER_URL': JSON.stringify(process.env.CONTENT_SERVER_URL || 'https://api.saigon.quest'),
    'process.env.CONTENT_SERVER_URL': JSON.stringify(process.env.CONTENT_SERVER_URL || 'https://content.saigon.quest'),
  })
];

