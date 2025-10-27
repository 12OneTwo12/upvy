module.exports = function (api) {
  api.cache(true);

  const plugins = ['nativewind/babel'];

  // Add reanimated plugin only for non-test environments
  if (process.env.NODE_ENV !== 'test') {
    plugins.push('react-native-reanimated/plugin');
  }

  return {
    presets: ['babel-preset-expo'],
    plugins,
  };
};
