# rift-java native libraries

The download/verify/package pipeline (issue #9) has landed. The `natives-bundle` Maven profile runs
`NativesFetcher` to download the pinned Rift engine release's `librift_ffi` cdylibs per
`ffi-manifest.json`, SHA-256 verifies each one, and stages them under
`native/<classifier>/librift_ffi.<ext>`. `maven-jar-plugin` then packages one classifier jar per
platform, each carrying its own `native/<classifier>/librift_ffi.<ext>` plus a sibling
`BUILD_INFO.json`. This resource root ships in every jar (including this default build) so the
embedded transport's native resolver (issue #10) can find it on the classpath.
