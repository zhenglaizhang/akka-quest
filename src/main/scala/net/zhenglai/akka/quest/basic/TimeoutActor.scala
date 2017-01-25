package net.zhenglai.akka.quest.basic

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, InvalidActorNameException, PoisonPill, Props, ReceiveTimeout, Terminated }
import scala.concurrent.duration._

// TODO: more...
class TimeoutActor extends Actor with ActorLogging {
  // defines the inactivity timeout after which the sending of a ReceiveTimeout message is triggered
  context.setReceiveTimeout(30 milliseconds)

  // the receive timeout might fire and enqueue the ReceiveTimeout message right after another message was enqueued;
  // hence it is not guaranteed that upon reception of the receive timeout there must have been an idle period beforehand as configured via this method.

  // Messages marked with NotInfluenceReceiveTimeout will not reset the timer.
  // This can be useful when ReceiveTimeout should be fired by external inactivity but not influenced by internal activity, e.g. scheduled tick messages.

  val child: ActorRef = context.actorOf(MagicNumberActor.props(12), name = "magicNumberActor")

  override def receive = {
    case "hello" =>
      log.info("Receive hello")
      context.setReceiveTimeout(300 milliseconds)
    case "interrupt-child" =>
      // Typically the context is used for stopping the actor itself or child actors and the system for stopping top level actors.
      // The actual termination of the actor is performed asynchronously, i.e. stop may return before the actor is stopped.
      // Processing of the current message, if any, will continue before the actor is stopped, but additional messages in the mailbox will not be processed.
      // By default these messages are sent to the deadLetters of the ActorSystem, but that depends on the mailbox implementation.
      context stop child

      // You can also send an actor the akka.actor.PoisonPill message, which will stop the actor when the message is processed. PoisonPill is enqueued as ordinary messages and will be handled after messages that were already queued in the mailbox.
      //      context ! PoisonPill
      try {
        val _ = context.actorOf(Props(new TimeoutActor), name = "magicNumberActor")
      } catch {
        case _: InvalidActorNameException =>
          log.warning("InvalidActorNameException caught, `context stop someActor` is async")
      }
    case "done" =>

      // Termination of an actor proceeds in two steps:
      //  1. first the actor suspends its mailbox processing and sends a stop command to all its children,
      //  2. then it keeps processing the internal termination notifications from its children until the last one is gone, finally terminating itself (invoking postStop, dumping mailbox, publishing Terminated on the DeathWatch, telling its supervisor).
      //
      // This procedure ensures that actor system sub-trees terminate in an orderly fashion, propagating the stop command to the leaves and collecting their confirmation back to the stopped supervisor.
      // If one of the actors does not respond (i.e. processing a message for extended periods of time and therefore not receiving the stop command), this whole process will be stuck.
      context stop self
    case Terminated(target) =>
      log.info("{} is terminated.", target)
    case ReceiveTimeout =>
      // turn off
      log.info("receiving timeout...")
      context.setReceiveTimeout(Duration.Undefined)
    //      throw new RuntimeException("Receive timed out")
  }
}

object ReceiveTimeoutActorMain extends App {
  val system = ActorSystem("ReceiveTimeoutActorMain")

  val actor = system.actorOf(Props(new TimeoutActor), name = "receiveTimeoutActor")


  actor ! "hello"

  actor ! "interrupt-child"

  actor ! "done"

  Thread.sleep(1000)


  system.terminate()
}
