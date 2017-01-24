package net.zhenglai.akka.quest.basic

import akka.actor.{ Actor, ActorLogging, PoisonPill, Props }
import net.zhenglai.akka.quest.basic.MagicNumberActor.{ Goodbye, Greeting }

/*
Akka enforces parental supervision every actor is supervised and (potentially) the supervisor of its children
 */
class EchoActor extends Actor with ActorLogging {

  //  val log = Logging(context.system, this)

  // PartialFunction[Any, Unit]
  // message loop is exhaustive, otherwise UnhandledMessage(msg, sender, recipient) will be published to the ActorSystem's EventStream
  // partial function object, stored within the actor as its initial behavior


  // create new child actor
  // It is recommended to create a hierarchy of children, grand-children
  // and so on such that it fits the logical failure-handling structure of the application,
  // [akka://mySystem/user/echoActor/magicNumber]
  val child = context.actorOf(MagicNumberActor.props(1), "magicNumber")

  log.info("children: {}", context.children)

  // lifecycle hooks
  // called when the actor is created
  override def preStart() = {
    log.info("pre starting...")
  }

  // All actors are supervised, i.e. linked to another actor with a fault handling strategy.

  // The old actor is informed by calling preRestart
  override def preRestart(reason: Throwable, message: Option[Any]) = {
    // This method is the best place for cleaning up, preparing hand-over to the fresh actor instance,
    // etc. By default it stops all children and calls postStop.
    super.preRestart(reason, message)
    log.error("pre restarting, reason={}, message={}", reason, message)
  }

  // The initial factory from the actorOf call is used to produce the fresh instance.

  // The new actor’s postRestart method is invoked with the exception which caused the restart.
  // By default the preStart is called, just as in the normal start-up case.
  override def postRestart(reason: Throwable) = {
    super.postRestart(reason) // call preStart() by default
    log.error("post restarting, reason={}", reason)
  }

  /*
  An actor restart replaces only the actual actor object;
  the contents of the mailbox is unaffected by the restart, so processing of messages will resume after the postRestart hook returns.
  The message that triggered the exception will not be received again.
  Any message sent to an actor while it is being restarted will be queued to its mailbox as usual.

   the ordering of failure notifications relative to user messages is not deterministic.
   In particular, a parent might restart its child before it has processed the last messages sent by the child before the failure.
   */

  // called async after actor.stop() is called
  override def postStop() = {
    //  for deregistering this actor from other services.
    // This hook is guaranteed to run after message queuing has been disabled for this actor,
    // i.e. messages sent to a stopped actor will be redirected to the deadLetters of the ActorSystem.
    log.info("good bye, message queuing must be disabled...")
  }

  def receive: Actor.Receive = {
    case "ping" => log.info("received ping")
    case Greeting(greeter) => log.info("greeted by {}", greeter)
    case Goodbye =>
      child ! PoisonPill
    case num: Int =>
      // child.forward(num)
      log.info("received num:{}", num)
      child ! num
    case _ => log.info("received unknown message")
  }

  // akka.actor.UnhandledMessage(message, sender, recipient) will be published to the ActorSystem's EventStream.
}


class MagicNumberActor(magicNumber: Int) extends Actor with ActorLogging {

  // import members of context to save keystrokes
  import context._

  log.info("my_path={}, parent_path={}", self.path, parent.path)

  // strategy to supervise childs
  // since failure is communicated as a message sent to the supervisor
  // and processed like other messages (albeit outside of the normal behavior),
  override def supervisorStrategy = super.supervisorStrategy

  // behavior of the actor
  def receive = {
    case num: Int =>
      log.info("received num: {}", num)
      if (num >= 999) {
        // sender() refs sender actor of the last received message
        sender() ! (num + magicNumber)
      }
  }

  override def unhandled(message: Any) = {
    log.info("unhandled message caught: {}", message)
  }

  override def postStop() = {
    log.info("good bye...")
  }
}

object MagicNumberActor {

  // Another good practice is to declare what messages an Actor can receive in the companion object of the Actor
  case class Greeting(from: String)

  case object Goodbye

  // Props: configuration class to specify options for the creation of actors
  // It is a good idea to provide factory methods on the companion object of each Actor
  // which help keeping the creation of suitable Props as close to the actor definition as possible.
  def props(magicNUmber: Int): Props = Props(new MagicNumberActor(magicNUmber))
}
