# Copyright 2021 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Diffs optimized integration test JS with current CL changes."""

import repo_util


def main(argv):
  test_name = argv.test_name[0]
  # TODO(goktug): run diff builds in parallel.
  # TODO(goktug): move these methods from repo_util to here.
  target = repo_util.get_readable_optimized_test(test_name)
  repo_util.diff_target(test_name, target)


def add_arguments(parser):
  parser.add_argument(
      "test_name", nargs=1, metavar="<name>", help="integration test name")