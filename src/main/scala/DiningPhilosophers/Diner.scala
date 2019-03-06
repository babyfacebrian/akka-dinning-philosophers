package DiningPhilosophers

import DiningPhilosophers.DinerSupervisor.OpenDinner
import akka.actor.{ActorSystem, Props}

object Diner extends App {

  val numberOfPhilosophers = 7

  // Create our actor system
  val dinerSystem = ActorSystem("FancyDiner")

  // Create our supervisor actor within our Diner system
  val supervisor = dinerSystem.actorOf(Props(classOf[DinerSupervisor], numberOfPhilosophers), "Manager")

  // "Tell" our supervisor to open the diner
  supervisor ! OpenDinner(numberOfPhilosophers)
}
