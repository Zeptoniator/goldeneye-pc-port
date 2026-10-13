# libgeload.so (Android): loads libmain.so at a fixed address, see
# ge_fixedload.c. A directory of its own so it is built before, and without,
# the game's directory-wide flags (libmain.map, savestate.ld, the decomp's
# header shims).
add_library(geload SHARED ge_fixedload.c)
target_link_libraries(geload dl log)
target_link_options(geload PRIVATE -Wl,-z,max-page-size=16384)
