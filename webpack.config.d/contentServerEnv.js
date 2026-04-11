// Inject CONTENT_SERVER_URL and API_SERVER_URL for Kotlin/JS via process.env
const webpack = require('webpack');

const apiUrl = process.env.API_SERVER_URL || process.env.API_BASE_URL || 'https://api.saigon.quest';
const contentUrl = process.env.CONTENT_SERVER_URL || 'https://content.saigon.quest';

console.log('[webpack.config.d] Applying DefinePlugin with API URL:', apiUrl);
console.log('[webpack.config.d] CONTENT URL:', contentUrl);

config.plugins = [
    ...(config.plugins || []),
    new webpack.DefinePlugin({
        'process.env.API_SERVER_URL': JSON.stringify(apiUrl),
        'process.env.CONTENT_SERVER_URL': JSON.stringify(contentUrl),
    })
];
