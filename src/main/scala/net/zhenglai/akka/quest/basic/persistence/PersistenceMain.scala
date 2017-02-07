package net.zhenglai.akka.quest.basic.persistence

import akka.persistence.{ PersistentActor, SnapshotOffer }

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

  override def persistenceId = "sample-id-1"

  var state = ExampleState()

  def updateState(event: Evt): Unit = state = state.updated(event)

  def numEvents = state.size

  override def receiveRecover: Receive = {
    case evt: Evt => updateState(evt)
    case SnapshotOffer(_, snapshot: ExampleState) => state = snapshot
  }

  override def receiveCommand: Receive = {
    case Cmd(data) =>
      // wow, validating the Cmd per current events state, and extract safe Evt
      persist(Evt(s"$data-$numEvents"))(updateState)
      persist(Evt(s"$data-${numEvents + 1}")) { event =>
        updateState(event)
        context.system.eventStream.publish(event)
      }
    case "snap" => saveSnapshot(state)
    case "print" => println(state)
  }

}

object PersistenceMain extends App {

}