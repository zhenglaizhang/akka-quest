package net.zhenglai.akka.quest.basic.persistence

import akka.actor.{ ActorSystem, Props }
import akka.persistence._
import akka.stream.ActorMaterializer

// The key concept behind Akka persistence is that only changes to an actor's internal state
// are persisted but never its current state directly (except for optional snapshots).
// These changes are only ever appended to storage, nothing is ever mutated, which allows
// for very high transaction rates and efficient replication. Stateful actors are recovered by
// replaying stored changes to these actors from which they can rebuild internal state. This
// can be either the full history of changes or starting from a snapshot which can dramatically
// reduce recovery times. Akka persistence also provides point-to-point communication with
// at-least-once message delivery semantics.


// PLUGINs:
// in-memory heap based journal,
// local file-system based snapshot-store
// LevelDB based journal.

// A persistent actor receives a (non-persistent) command which is first validated if it can be applied to the current state. Here
// validation can mean anything, from simple inspection of a command message's fields up to a conversation with several external
// services, for example. If validation succeeds, events are generated from the command, representing the effect of the command. These
// events are then persisted and, after successful persistence, used to change the actor's state. When the persistent actor needs to be
// recovered, only the persisted events are replayed of which we know that they can be successfully applied. In other words, events
// cannot fail when being replayed to a persistent actor, in contrast to commands. Event sourced actors may of course also process
// commands that do not change application state such as query commands for example.

case class Cmd(data: String)

case class Evt(data: String)

case class ExampleState(events: List[String] = Nil) {
  def updated(evt: Evt): ExampleState = copy(evt.data :: events)

  def size: Int = events.length

  override def toString = events.reverse.toString
}

// The state of the ExamplePersistentActor is a list of persisted event data contained in ExampleState.
class ExamplePersistentActor extends PersistentActor {

  // Id of the persistent entity for which messages should be replayed.
  // Must be unique to a given entity in the journal(database table/keyspace)
  // message-replaying behavior depends on the persistenceId to query messages
  override def persistenceId = "sample-id-1"

  var state = ExampleState()

  def updateState(event: Evt): Unit = state = state.updated(event)

  def numEvents = state.size

  // By default, a persistent actor is automatically recovered on `start` and on `restart` by replaying journaled messages. New messages sent
  // to a persistent actor during recovery do not interfere with replayed messages. They are cached and received by a persistent actor
  // after recovery phase completes.
  override def receiveRecover: Receive = {
    // Accessing the sender() for replayed messages will always result in a deadLetters reference
    case evt: Evt => updateState(evt)
    case SnapshotOffer(_, snapshot: ExampleState) => state = snapshot
    case RecoveryCompleted =>
      println("recovery completed (start or restart)")
    // perform init right after recovery, before any othre messages
  }

  override def recovery = Recovery(
    fromSnapshot = SnapshotSelectionCriteria.Latest,
    toSequenceNr = Long.MaxValue,
    replayMax = Long.MaxValue
  )

  // Called whenever a message replay fails. By default it logs the error.
  // The actor is always stopped after this method has been invoked.
  override def onRecoveryFailure(cause: Throwable, event: Option[Any]) = super.onRecoveryFailure(cause, event)

  // The persist method persists events asynchronously and the event handler is executed for successfully persisted events. Successfully
  // persisted events are internally sent back to the persistent actor as individual messages that trigger event handler executions. An
  // event handler may close over persistent actor state and mutate it. The sender of a persisted event is the sender of the
  // corresponding command. This allows event handlers to reply to the sender of a command (not shown).
  override def receiveCommand: Receive = {
    case Cmd(data) =>
      // wow, validating the Cmd per current events state, and extract safe Evt

      // When persisting events with persist it is guaranteed that the persistent actor will not receive further commands between the
      // persist call and the execution(s) of the associated event handler.
      // onPersistFailure => logging and stop the actor
      // onPersistRejected
      persist(Evt(s"$data-$numEvents"))(updateState)
      persist(Evt(s"$data-${numEvents + 1}")) { event =>
        updateState(event)

        // notifying others about successful state changes by publishing events.
        context.system.eventStream.publish(event)
      }
    case "snap" => saveSnapshot(state)
    case "print" => println(state)
    // trigger restart & recovery
    case "boom" => throw new RuntimeException("boom")
    case SaveSnapshotSuccess(meta) =>
      println(s"SaveSnapshotSuccess($meta)")
  }

}

object PersistenceMain extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val persistentActor = system.actorOf(Props[ExamplePersistentActor], "persistentActor")

  persistentActor ! "print"
  persistentActor ! Cmd("foo")
  persistentActor ! Cmd("baz")
  persistentActor ! "boom"
  persistentActor ! Cmd("bar")
  persistentActor ! "snap"
  persistentActor ! Cmd("buzz")
  persistentActor ! "print"

  Thread.sleep(10000)
  system.terminate()
}
