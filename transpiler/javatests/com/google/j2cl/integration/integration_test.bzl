"""integration_test build macro

A build macro that turns Java files into optimized and unoptimized test targets.

The set of Java files must have a Main class with a main() function.


Example usage:

# Creates targets
# blaze test :compiled_test
# blaze test :uncompiled_test
integration_test(
    name = "foobar",
    srcs = glob(["*.java"]),
)

"""

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_test")
load("//build_defs:rules.bzl", "J2CL_TEST_DEFS", "j2cl_library")
load("//build_defs/internal_do_not_use:j2cl_util.bzl", "get_java_package")

JAVAC_FLAGS = [
    "-XepDisableAllChecks",
]

# TODO(b/119637659): abstract common behaviour and merge with the internal version.
def integration_test(
        name,
        srcs,
        deps = [],
        defs = [],
        main_class = None,
        closure_defines = dict(),
        suppress = [],
        j2cl_library_tags = [],
        tags = [],
        plugins = [],
        **kwargs):
    """Macro that turns Java files into integration test targets.

    deps are Labels of j2cl_library() rules. NOT labels of
    java_library() rules.
    """

    # figure out the current location
    java_package = get_java_package(native.package_name())

    if not main_class:
        main_class = java_package + ".Main"

    deps = deps + ["//transpiler/javatests/com/google/j2cl/integration/testing:testing-j2cl"]

    define_flags = ["--define=%s=%s" % (k, v) for (k, v) in closure_defines.items()]

    defs = defs + define_flags

    java_test_runner = """
      @jsinterop.annotations.JsType(namespace = jsinterop.annotations.JsPackage.GLOBAL)
      public class TestRunner {
        public static void testMain() throws Exception {
          %s.main((String []) null);
        }
      }
    """ % (main_class)
    _genfile("TestRunner.java", java_test_runner, tags)

    j2cl_library(
        name = "%s-TestRunner" % name,
        srcs = ["TestRunner.java"],
        generate_build_test = False,
        deps = [
            ":%s" % name,
            "@com_google_jsinterop_annotations-j2cl//:jsinterop-annotations-j2cl",
        ],
        javacopts = JAVAC_FLAGS,
        tags = tags + j2cl_library_tags,
    )

    j2cl_library(
        name = name,
        srcs = srcs,
        generate_build_test = False,
        deps = deps,
        javacopts = JAVAC_FLAGS,
        plugins = plugins,
        tags = tags + j2cl_library_tags,
        js_suppress = suppress,
    )

    # blaze test :uncompiled_test
    # blaze test :compiled_test

    test_harness = """
      goog.module('gen.test.Harness');
      goog.setTestOnly();

      var testSuite = goog.require('goog.testing.testSuite');
      var TestRunner = goog.require('TestRunner');

      testSuite({
        test_Main: function() {
          return TestRunner.testMain();
        }
      });
    """
    _genfile("TestHarness_test.js", test_harness, tags)

    closure_js_test(
        name = "compiled_test",
        srcs = ["TestHarness_test.js"],
        deps = [
            ":" + name,
            ":%s-TestRunner" % name,
            "@com_google_javascript_closure_library//closure/goog/testing:testsuite",
        ],
        # closure_js_test test infra is flaky so avoid noise in builds.
        flaky = True,
        defs = J2CL_TEST_DEFS + defs,
        suppress = suppress,
        testonly = True,
        tags = tags,
        entry_points = ["gen.test.Harness"],
    )

def _genfile(name, str, tags):
    native.genrule(
        name = name.replace(".", "_"),
        outs = [name],
        cmd = "echo \"%s\" > $@" % str,
        tags = tags,
    )
