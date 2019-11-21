/*
 * Copyright (C) 2018-2019 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.persistence.couchbase

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.{DeleteMessagesSuccess, PersistentActor, RecoveryCompleted}
import akka.persistence.couchbase.TestActor.{GetLastRecoveredEvent, SaveSnapshot}
import akka.persistence.journal.Tagged

import scala.collection.immutable

object TestActor {
  def props(persistenceId: String): Props = props(persistenceId, "couchbase-journal.write")

  def props(persistenceId: String, journalId: String): Props = Props(new TestActor(persistenceId, journalId))

  final case class PersistAll(events: immutable.Seq[String])
  final case class PersistAllAsync(events: immutable.Seq[String])
  final case class DeleteTo(seqNr: Long)
  final case object SaveSnapshot
  final case object GetLastRecoveredEvent
  case object Stop
}

class TestActor(override val persistenceId: String, override val journalPluginId: String)
    extends PersistentActor
    with ActorLogging {
  var lastDelete: ActorRef = _
  var lastRecoveredEvent: String = _

  val receiveRecover: Receive = {
    case evt: String =>
      lastRecoveredEvent = evt

    case RecoveryCompleted =>
      log.debug("Recovery completed, lastRecoveredEvent: {}", lastRecoveredEvent)
  }

  val receiveCommand: Receive = {
    case cmd: String =>
      persist(cmd) { evt =>
        sender() ! evt + "-done"
      }
    case cmd: Tagged =>
      persist(cmd) { evt =>
        val msg = s"${evt.payload}-done"
        sender() ! msg
      }

    case TestActor.PersistAll(events) =>
      val size = events.size
      val handler = {
        var count = 0
        _: String => {
          count += 1
          if (count == size)
            sender() ! "PersistAll-done"
        }
      }
      persistAll(events)(handler)

    case TestActor.PersistAllAsync(events) =>
      val size = events.size
      val handler = {
        var count = 0
        evt: String => {
          count += 1
          if (count == size)
            sender() ! "PersistAllAsync-done"
        }
      }
      persistAllAsync(events)(handler)
      sender() ! "PersistAllAsync-triggered"

    case TestActor.DeleteTo(seqNr) =>
      lastDelete = sender()
      deleteMessages(seqNr)

    case d: DeleteMessagesSuccess =>
      lastDelete ! d

    case SaveSnapshot =>
      saveSnapshot("dumb-snapshot-body")
      sender() ! snapshotSequenceNr

    case GetLastRecoveredEvent =>
      sender() ! lastRecoveredEvent

    case TestActor.Stop =>
      context.stop(self)
  }
}
