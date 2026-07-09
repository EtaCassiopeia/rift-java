# rift-java native libraries

This directory is populated by CI from a pinned Rift release: the `librift_ffi` cdylibs for each
platform (`native/<os>-<arch>/librift_ffi.<ext>`), packaged into per-platform classifier jars and
SHA-256 verified. The download/packaging pipeline is added in issue #9; this placeholder reserves
the resource layout that the embedded transport's native resolver (issue #10) reads from the
classpath.
