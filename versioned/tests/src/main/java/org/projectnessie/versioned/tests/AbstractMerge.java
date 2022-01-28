/*
 * Copyright (C) 2022 Dremio
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
package org.projectnessie.versioned.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.Commit;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.Key;
import org.projectnessie.versioned.Put;
import org.projectnessie.versioned.ReferenceConflictException;
import org.projectnessie.versioned.ReferenceNotFoundException;
import org.projectnessie.versioned.StringStoreWorker.TestEnum;
import org.projectnessie.versioned.VersionStore;
import org.projectnessie.versioned.VersionStoreException;

public abstract class AbstractMerge extends AbstractNestedVersionStore {
  protected AbstractMerge(VersionStore<String, String, TestEnum> store) {
    super(store);
  }

  private Hash initialHash;
  private Hash firstCommit;
  private Hash secondCommit;
  private Hash thirdCommit;
  private List<Commit<String, String>> commits;

  @BeforeEach
  protected void setupCommits() throws VersionStoreException {
    final BranchName branch = BranchName.of("foo");
    store().create(branch, Optional.empty());

    // The default common ancestor for all merge-tests.
    // The spec for 'VersionStore.merge' mentions "(...) until we arrive at a common ancestor",
    // but old implementations allowed a merge even if the "merge-from" and "merge-to" have no
    // common ancestor and did merge "everything" from the "merge-from" into "merge-to".
    // Note: "beginning-of-time" (aka creating a branch without specifying a "create-from")
    // creates a new commit-tree that is decoupled from other commit-trees.
    initialHash = commit("Default common ancestor").toBranch(branch);

    firstCommit =
        commit("First Commit")
            .put("t1", "v1_1")
            .put("t2", "v2_1")
            .put("t3", "v3_1")
            .toBranch(branch);
    secondCommit =
        commit("Second Commit")
            .put("t1", "v1_2")
            .delete("t2")
            .delete("t3")
            .put("t4", "v4_1")
            .toBranch(branch);
    thirdCommit = commit("Third Commit").put("t2", "v2_2").unchanged("t4").toBranch(branch);

    commits = commitsList(branch, false).subList(0, 3);
  }

  private String merged(String commitMeta) {
    return commitMeta + ", merged";
  }

  @Test
  protected void mergeIntoEmptyBranch() throws VersionStoreException {
    final BranchName newBranch = BranchName.of("mergeIntoEmptyBranch");
    store().create(newBranch, Optional.of(initialHash));

    store().merge(thirdCommit, newBranch, Optional.of(initialHash), Function.identity());
    assertThat(
            store()
                .getValues(
                    newBranch,
                    Arrays.asList(Key.of("t1"), Key.of("t2"), Key.of("t3"), Key.of("t4"))))
        .containsExactlyInAnyOrderEntriesOf(
            ImmutableMap.of(
                Key.of("t1"), "v1_2",
                Key.of("t2"), "v2_2",
                Key.of("t4"), "v4_1"));

    // not modifying commit meta, will just "fast forward"
    assertThat(store().hashOnReference(newBranch, Optional.empty())).isEqualTo(thirdCommit);

    assertCommitMeta(commitsList(newBranch, false).subList(0, 3), commits, Function.identity());
  }

  @Test
  protected void mergeIntoEmptyBranchModifying() throws VersionStoreException {
    final BranchName newBranch = BranchName.of("mergeIntoEmptyBranchModifying");
    store().create(newBranch, Optional.of(initialHash));

    store().merge(thirdCommit, newBranch, Optional.of(initialHash), this::merged);
    assertThat(
            store()
                .getValues(
                    newBranch,
                    Arrays.asList(Key.of("t1"), Key.of("t2"), Key.of("t3"), Key.of("t4"))))
        .containsExactlyInAnyOrderEntriesOf(
            ImmutableMap.of(
                Key.of("t1"), "v1_2",
                Key.of("t2"), "v2_2",
                Key.of("t4"), "v4_1"));

    // modify the commit meta, will generate new commits and therefore new commit hashes
    assertThat(store().hashOnReference(newBranch, Optional.empty())).isNotEqualTo(thirdCommit);

    assertCommitMeta(commitsList(newBranch, false).subList(0, 3), commits, this::merged);
  }

  @Test
  protected void mergeIntoNonConflictingBranch() throws VersionStoreException {
    final BranchName newBranch = BranchName.of("bar_2");
    store().create(newBranch, Optional.of(initialHash));
    final Hash newCommit = commit("Unrelated commit").put("t5", "v5_1").toBranch(newBranch);

    store().merge(thirdCommit, newBranch, Optional.empty(), Function.identity());
    assertThat(
            store()
                .getValues(
                    newBranch,
                    Arrays.asList(
                        Key.of("t1"), Key.of("t2"), Key.of("t3"), Key.of("t4"), Key.of("t5"))))
        .containsExactlyInAnyOrderEntriesOf(
            ImmutableMap.of(
                Key.of("t1"), "v1_2",
                Key.of("t2"), "v2_2",
                Key.of("t4"), "v4_1",
                Key.of("t5"), "v5_1"));

    final List<Commit<String, String>> commits = commitsList(newBranch, false);
    assertThat(commits)
        .satisfiesExactly(
            c0 -> assertThat(c0).extracting(Commit::getCommitMeta).isEqualTo("Third Commit"),
            c1 -> assertThat(c1).extracting(Commit::getCommitMeta).isEqualTo("Second Commit"),
            c2 -> assertThat(c2).extracting(Commit::getCommitMeta).isEqualTo("First Commit"),
            c3 -> assertThat(c3).extracting(Commit::getHash).isEqualTo(newCommit),
            c4 -> assertThat(c4).extracting(Commit::getHash).isEqualTo(initialHash));
  }

  @Test
  protected void nonEmptyFastForwardMerge() throws VersionStoreException {
    final Key key = Key.of("t1");
    final BranchName etl = BranchName.of("etl");
    final BranchName review = BranchName.of("review");
    store().create(etl, Optional.of(initialHash));
    store().create(review, Optional.of(initialHash));
    store()
        .commit(
            etl, Optional.empty(), "commit 1", Collections.singletonList(Put.of(key, "value1")));
    store()
        .merge(
            store().hashOnReference(etl, Optional.empty()),
            review,
            Optional.empty(),
            Function.identity());
    store()
        .commit(
            etl, Optional.empty(), "commit 2", Collections.singletonList(Put.of(key, "value2")));
    store()
        .merge(
            store().hashOnReference(etl, Optional.empty()),
            review,
            Optional.empty(),
            Function.identity());
    assertEquals(store().getValue(review, key), "value2");
  }

  @Test
  protected void mergeWithCommonAncestor() throws VersionStoreException {
    final BranchName newBranch = BranchName.of("bar_2");
    store().create(newBranch, Optional.of(firstCommit));

    final Hash newCommit = commit("Unrelated commit").put("t5", "v5_1").toBranch(newBranch);

    store().merge(thirdCommit, newBranch, Optional.empty(), Function.identity());
    assertThat(
            store()
                .getValues(
                    newBranch,
                    Arrays.asList(
                        Key.of("t1"), Key.of("t2"), Key.of("t3"), Key.of("t4"), Key.of("t5"))))
        .containsExactlyInAnyOrderEntriesOf(
            ImmutableMap.of(
                Key.of("t1"), "v1_2",
                Key.of("t2"), "v2_2",
                Key.of("t4"), "v4_1",
                Key.of("t5"), "v5_1"));

    final List<Commit<String, String>> commits = commitsList(newBranch, false);
    assertThat(commits).hasSize(5);
    assertThat(commits.get(4).getHash()).isEqualTo(initialHash);
    assertThat(commits.get(3).getHash()).isEqualTo(firstCommit);
    assertThat(commits.get(2).getHash()).isEqualTo(newCommit);
    assertThat(commits.get(1).getCommitMeta()).isEqualTo("Second Commit");
    assertThat(commits.get(0).getCommitMeta()).isEqualTo("Third Commit");
  }

  @Test
  protected void mergeWithConflictingKeys() throws VersionStoreException {
    final BranchName foo = BranchName.of("foofoo");
    final BranchName bar = BranchName.of("barbar");
    store().create(foo, Optional.of(this.initialHash));
    store().create(bar, Optional.of(this.initialHash));

    // we're essentially modifying the same key on both branches and then merging one branch into
    // the other and expect a conflict
    Key key1 = Key.of("some_key1");
    Key key2 = Key.of("some_key2");

    store()
        .commit(
            foo, Optional.empty(), "commit 1", Collections.singletonList(Put.of(key1, "value1")));
    store()
        .commit(
            bar, Optional.empty(), "commit 2", Collections.singletonList(Put.of(key1, "value2")));
    store()
        .commit(
            foo, Optional.empty(), "commit 3", Collections.singletonList(Put.of(key2, "value3")));
    Hash barHash =
        store()
            .commit(
                bar,
                Optional.empty(),
                "commit 4",
                Collections.singletonList(Put.of(key2, "value4")));

    assertThatThrownBy(() -> store().merge(barHash, foo, Optional.empty(), Function.identity()))
        .isInstanceOf(ReferenceConflictException.class)
        .hasMessageContaining("The following keys have been changed in conflict:")
        .hasMessageContaining(key1.toString())
        .hasMessageContaining(key2.toString());
  }

  @Test
  protected void mergeIntoConflictingBranch() throws VersionStoreException {
    final BranchName newBranch = BranchName.of("bar_3");
    store().create(newBranch, Optional.of(initialHash));
    commit("Another commit").put("t1", "v1_4").toBranch(newBranch);

    assertThrows(
        ReferenceConflictException.class,
        () -> store().merge(thirdCommit, newBranch, Optional.of(initialHash), Function.identity()));
  }

  @Test
  protected void mergeIntoNonExistingBranch() {
    final BranchName newBranch = BranchName.of("bar_5");
    assertThrows(
        ReferenceNotFoundException.class,
        () -> store().merge(thirdCommit, newBranch, Optional.of(initialHash), Function.identity()));
  }

  @Test
  protected void mergeIntoNonExistingReference() throws VersionStoreException {
    final BranchName newBranch = BranchName.of("bar_6");
    store().create(newBranch, Optional.of(initialHash));
    assertThrows(
        ReferenceNotFoundException.class,
        () ->
            store()
                .merge(
                    Hash.of("1234567890abcdef"),
                    newBranch,
                    Optional.of(initialHash),
                    Function.identity()));
  }
}