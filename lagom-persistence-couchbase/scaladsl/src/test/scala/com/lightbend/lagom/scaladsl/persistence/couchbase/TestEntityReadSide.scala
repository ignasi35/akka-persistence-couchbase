/*
 * Copyright (C) 2018 Lightbend Inc. <http://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence.couchbase

import akka.Done
import akka.actor.ActorSystem
import akka.stream.alpakka.couchbase.scaladsl.CouchbaseSession
import com.couchbase.client.java.document.JsonDocument
import com.couchbase.client.java.document.json.JsonObject
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, EventStreamElement, ReadSideProcessor, TestEntity}

import scala.concurrent.{ExecutionContext, Future}

object TestEntityReadSide {
  class TestEntityReadSideProcessor(system: ActorSystem, readSide: CouchbaseReadSide)
      extends ReadSideProcessor[TestEntity.Evt] {

    @volatile private var prepared = false

    def buildHandler(): ReadSideHandler[TestEntity.Evt] = {
      import system.dispatcher

      def updateCount(cs: CouchbaseSession, element: EventStreamElement[TestEntity.Appended]): Future[Done] =
        getCount(cs, element.entityId)
          .flatMap((count: Long) => {
            if (!prepared) {
              throw new IllegalStateException("Prepare handler hasn't been called")
            }
            cs.upsert(
              JsonDocument.create(s"count-${element.entityId}", JsonObject.create().put("count", count + 1))
            )
          })
          .map(_ => Done)

      readSide
        .builder[TestEntity.Evt]("testoffsets")
        .setGlobalPrepare(session => Future.successful(Done))
        .setPrepare(
          (session, tag) =>
            Future {
              prepared = true
              Done
          }
        )
        .setEventHandler[TestEntity.Appended](updateCount)
        .build()
    }

    def aggregateTags: Set[AggregateEventTag[TestEntity.Evt]] = TestEntity.Evt.aggregateEventShards.allTags

  }

  def getCount(session: CouchbaseSession, entityId: String)(implicit ec: ExecutionContext): Future[Long] =
    session.get(s"count-$entityId").map {
      case Some(l) => l.content().getLong("count")
      case None => 0L
    }

}

class TestEntityReadSide(system: ActorSystem, couchbase: CouchbaseSession) {

  import system.dispatcher

  def getAppendCount(entityId: String): Future[Long] =
    TestEntityReadSide.getCount(couchbase, entityId)
}
