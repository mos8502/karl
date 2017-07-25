# karl
Karl is a gradle plugin for generating Android resource extension properties for
- Context
- View
- Activity
- Fragment

Currently properties are only generated for the following resource types
- color
- dimension
- integer
- drawable
- string

For each class (Context, View, Activity, Fragment) an extensions property is generated with colors, dimens, ints, drawables and strings respectively. Each property provides access to an immutable resource class with properties for each resource in the cathegory. The resource value are cached internally. 

# Usage

Currently the plugin is not available through online maven repositories. 

1. Publish the plugin to your local maven repo

```
./gradlew clean  publishToMavenLocal
```
2. Make the following changes in build.grade

```
buildscript {
    ...
    repositories {
        ...
        // where the plugin resides   
        mavenLocal()
        // the plugin uses snapshot version of [Kotlin](https://github.com/square/kotlinpoet)
        maven { url 'https://oss.sonatype.org/content/repositories/snapshots' }
        ...
    }
    
    dependencies {
        ...
        classpath 'hu.nemi.karl:karl-plugin:0.1'
        ...
    }
    ...
}
```
3. Finally, apply the plugin by adding
```
apply plugin: 'hu.nemi.karl'
```
to build.gradle

Once you build your project ResourcesExt.kt will be generated in your application's package. 

