/*
 * Copyright (2021) The Delta Lake Project Authors.
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

package org.apache.spark.sql.delta.coordinatedcommits

import scala.collection.mutable

import io.delta.dynamodbcommitcoordinator.DynamoDBCommitCoordinatorClientBuilder
import io.delta.storage.commit.CommitCoordinatorClient

import org.apache.spark.sql.SparkSession

object CommitCoordinatorClient {
  def semanticEquals(
      commitCoordinatorClientOpt1: Option[CommitCoordinatorClient],
      commitCoordinatorClientOpt2: Option[CommitCoordinatorClient]): Boolean = {
    (commitCoordinatorClientOpt1, commitCoordinatorClientOpt2) match {
      case (Some(commitCoordinatorClient1), Some(commitCoordinatorClient2)) =>
        commitCoordinatorClient1.semanticEquals(commitCoordinatorClient2)
      case (None, None) =>
        true
      case _ =>
        false
    }
  }
}

/** A builder interface for [[CommitCoordinatorClient]] */
trait CommitCoordinatorBuilder {

  /** Name of the commit-coordinator */
  def getName: String

  /** Returns a commit-coordinator client based on the given conf */
  def build(spark: SparkSession, conf: Map[String, String]): CommitCoordinatorClient
}

/** An extended builder interface for [[CommitCoordinatorClient]] with CatalogOwned table feature */
trait CatalogOwnedCommitCoordinatorBuilder extends CommitCoordinatorBuilder {
  /** Returns a catalog-owned commit-coordinator client based for the given catalog. */
  def buildForCatalog(spark: SparkSession, catalogName: String): CommitCoordinatorClient
}

/** Factory to get the correct [[CommitCoordinatorClient]] for a table */
object CommitCoordinatorProvider {
  // mapping from different commit-coordinator names to the corresponding
  // [[CommitCoordinatorBuilder]]s.
  private val nameToBuilderMapping = mutable.Map.empty[String, CommitCoordinatorBuilder]

  /** Registers a new [[CommitCoordinatorBuilder]] with the [[CommitCoordinatorProvider]] */
  def registerBuilder(commitCoordinatorBuilder: CommitCoordinatorBuilder): Unit = synchronized {
    nameToBuilderMapping.get(commitCoordinatorBuilder.getName) match {
      case Some(existingBuilder: CommitCoordinatorBuilder) =>
        throw new IllegalArgumentException(
          s"commit-coordinator: ${existingBuilder.getName} already" +
          s" registered with builder ${existingBuilder.getClass.getName}")
      case None =>
        nameToBuilderMapping.put(commitCoordinatorBuilder.getName, commitCoordinatorBuilder)
    }
  }

  /**
   * Returns a [[CommitCoordinatorClient]] for the given `name`, `conf`, and `spark`.
   * If the commit-coordinator with the given name is not registered, an exception is thrown.
   */
  def getCommitCoordinatorClient(
      name: String,
      conf: Map[String, String],
      spark: SparkSession): CommitCoordinatorClient = synchronized {
    getCommitCoordinatorClientOpt(name, conf, spark).getOrElse {
      throw new IllegalArgumentException(s"Unknown commit-coordinator: $name")
    }
  }

  /**
   * Returns a [[CommitCoordinatorClient]] for the given `name`, `conf`, and `spark`.
   * Returns None if the commit-coordinator with the given name is not registered.
   */
  def getCommitCoordinatorClientOpt(
      name: String,
      conf: Map[String, String],
      spark: SparkSession): Option[CommitCoordinatorClient] = synchronized {
    nameToBuilderMapping.get(name).map(_.build(spark, conf))
  }

  def getRegisteredCoordinatorNames: Seq[String] = synchronized {
    nameToBuilderMapping.keys.toSeq
  }

  // Visible only for UTs
  private[delta] def clearNonDefaultBuilders(): Unit = synchronized {
    val initialCommitCoordinatorNames = initialCommitCoordinatorBuilders.map(_.getName).toSet
    nameToBuilderMapping.retain((k, _) => initialCommitCoordinatorNames.contains(k))
  }

  private[delta] def clearAllBuilders(): Unit = synchronized {
    nameToBuilderMapping.clear()
  }

  private val initialCommitCoordinatorBuilders = Seq[CommitCoordinatorBuilder](
    UCCommitCoordinatorBuilder,
    new DynamoDBCommitCoordinatorClientBuilder()
  )
  initialCommitCoordinatorBuilders.foreach(registerBuilder)
}

/** Factory to get the correct [[CatalogOwnedCommitCoordinatorBuilder]] for a catalog-owned table */
object CatalogOwnedCommitCoordinatorProvider {
  // mapping from catalog names to the corresponding [[CatalogOwnedCommitCoordinatorBuilder]]s.
  private val catalogNameToBuilderMapping =
    mutable.Map.empty[String, CatalogOwnedCommitCoordinatorBuilder]

  // Visible only for UTs
  private[delta] def clearBuilders(): Unit = synchronized {
    catalogNameToBuilderMapping.clear()
  }

  /** Registers a new [[CommitCoordinatorBuilder]] with the [[CommitCoordinatorProvider]] */
  def registerBuilder(
      catalogName: String, commitCoordinatorBuilder: CatalogOwnedCommitCoordinatorBuilder): Unit =
    synchronized {
      catalogNameToBuilderMapping.get(catalogName) match {
        case Some(existingBuilder: CommitCoordinatorBuilder) =>
          throw new IllegalArgumentException(
            s"commit-coordinator for catalog: $catalogName already" +
              s" registered with builder ${existingBuilder.getClass.getName}")
        case None =>
          catalogNameToBuilderMapping.put(catalogName, commitCoordinatorBuilder)
      }
    }

  def getBuilder(catalogName: String): Option[CatalogOwnedCommitCoordinatorBuilder] =
    catalogNameToBuilderMapping.get(catalogName)
}
