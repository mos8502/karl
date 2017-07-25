# karl
Karl is a gradle plugin for generating android resource extension properties for
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
