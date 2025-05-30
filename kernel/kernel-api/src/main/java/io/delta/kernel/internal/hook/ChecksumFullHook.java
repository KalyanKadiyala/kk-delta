/*
 * Copyright (2025) The Delta Lake Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.delta.kernel.internal.hook;

import static io.delta.kernel.internal.util.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.delta.kernel.Table;
import io.delta.kernel.engine.Engine;
import io.delta.kernel.hook.PostCommitHook;
import io.delta.kernel.internal.fs.Path;
import java.io.IOException;

/**
 * A post-commit hook that writes a new checksum file at the version committed by the transaction.
 * This hook performs a writing checksum operation with table state construction for log replay.
 */
public class ChecksumFullHook implements PostCommitHook {

  private final Path tablePath;
  private final long version;

  public ChecksumFullHook(Path tablePath, long version) {
    this.tablePath = requireNonNull(tablePath);
    this.version = version;
  }

  @Override
  public void threadSafeInvoke(Engine engine) throws IOException {
    checkArgument(engine != null);
    Table.forPath(engine, tablePath.toString()).checksum(engine, version);
  }

  @Override
  public PostCommitHookType getType() {
    return PostCommitHookType.CHECKSUM_FULL;
  }
}
