python_library(
  name = 'core',
  sources = globs('*.py'),
  dependencies = [
    '3rdparty/python:ansicolors',
    '3rdparty/python:configparser',
    '3rdparty/python:future',
    '3rdparty/python:pantsbuild.pants',
    '3rdparty/python:typing',
    'src/python/fsqio/pants/buildgen/core/subsystems',
  ],
  tags = [
    'opensource',
    'buildgen',
    'dependencies_must_have:opensource',
    'dependencies_must_have:buildgen',
  ],
  provides=setup_py(
    name='fsqio.pants.buildgen.core',
    version='1.3.1',
    description='Core libraries supporting Pants buildgen.',
    url='https://github.com/foursquare/fsqio',
    license='Apache License, Version 2.0',
    zip_safe=True,
    namespace_packages=['fsqio'],
    classifiers=[
      'Intended Audience :: Developers',
      'License :: OSI Approved :: Apache Software License',
      'Operating System :: MacOS :: MacOS X',
      'Operating System :: POSIX :: Linux',
      'Programming Language :: Python',
      'Topic :: Software Development :: Build Tools',
      'Topic :: Software Development :: Testing',
    ],
  ),
)
