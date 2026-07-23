/*
 * Copyright 2020 Kirill5k
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mongo4cats.test

import java.net.ServerSocket

object FreePort {

  // Allocates a currently-free OS-assigned port. Must be called right before starting mongod (at test-run
  // time, not spec-construction time) so each embedded instance binds a distinct, currently-available port
  // instead of reusing a fixed one, which was causing "Address already in use" flakiness on CI.
  def next(): Int = {
    val socket = new ServerSocket(0)
    try socket.getLocalPort
    finally socket.close()
  }
}
