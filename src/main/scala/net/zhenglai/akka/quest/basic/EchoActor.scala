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
  override def preStart() = {
    super.preStart()
    log.info("pre starting...")
  }

  override def preRestart(reason: Throwable, message: Option[Any]) = {
    super.preRestart(reason, message)
    log.error("pre restarting, reason={}, message={}", reason, message)
  }

  override def postRestart(reason: Throwable) = {
    super.postRestart(reason)
    log.error("post restarting, reason={}", reason)
  }

  // called async after actor.stop() is called
  override def postStop() = {
    log.info("good bye...")
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
