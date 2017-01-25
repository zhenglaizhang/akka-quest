package net.zhenglai.akka.quest.basic

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }

class HotSwapActor extends Actor with ActorLogging {

  import context._

  def angry: Receive = {
    case "go_angry" => sender() ! "I am already angry!"; log.warning("I am already angry")
    case "go_happy" => log.info("happy"); become(happy);
  }

  def happy: Receive = {
    case "go_happy" => sender() ! "I am already happy!"; log.warning("I am already happy")
    case "go_angry" => log.info("angry"); become(angry)
  }

  override def receive = {
    case "go_angry" => log.info("angry"); become(angry)
    case "go_happy" => log.info("happy"); become(happy)
  }

}

case object Swap

class Swapper extends Actor with ActorLogging {

  import context._

  // todo: https://github.com/akka/akka/blob/v2.4.16/akka-docs/rst/scala/code/docs/actor/UnnestedReceives.scala
  override def receive = {
    case Swap =>
      log.info("Hi")
      become({
        case Swap =>
          log.info("Ho")
          unbecome() // resets the latest `become` (just for fun)
      }, discardOld = false) // push on top instead of replace
  }
}

object HotSwapActorMain extends App {

  // hot swap the actor's message loop
  // The hot swapped code is kept in a Stack which can be pushed and popped

  val system: ActorSystem = ActorSystem()
  val swap = system.actorOf(Props[Swapper], name = "swapper")

  val log = system.log

  Iterator.range(0, 6).foreach(_ => swap ! Swap)

  println("-" * 120)

  val hotSwap = system.actorOf(Props[HotSwapActor], name = "hotSwapper")
  hotSwap ! "go_angry"
  hotSwap ! "go_angry"
  hotSwap ! "go_happy"
  hotSwap ! "go_happy"
  hotSwap ! "go_angry"
  hotSwap ! "go_happy"

  Thread.sleep(1000)
  system.terminate()
}
