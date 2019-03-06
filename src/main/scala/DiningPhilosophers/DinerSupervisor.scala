package DiningPhilosophers


import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}

object DinerSupervisor {
  case class OpenDinner(seats: Int)
}

class DinerSupervisor(val maxMeals: Int) extends Actor with ActorLogging {
  import DinerSupervisor._
  import Philosopher._

  // Shut down if all actors have eaten the max meals
  private def checkTables(tables: Map[ActorRef, Int]): Unit = {
    val check = tables.values.forall(_ == this.maxMeals)
    if (check) {
      context.system.terminate()
    }
  }

  override def receive: Receive = {
    case OpenDinner(seats) =>
      // Create our fork actors
      val forks = for (i <- 1 to seats) yield context.actorOf(Props[Fork], s"fork$i")

      // Create n number of philosophers
      val philosophers = for ((name, i) <- List.fill(seats)("philosopher").zipWithIndex)
        yield context.actorOf(Props(classOf[Philosopher], forks(i), forks((i + 1) % seats)), s"$name${i+1}")

      val tableMap = philosophers.map(p => (p, 0)).toMap  // starting map

      philosophers.foreach(_ ! Think)  // all philosophers sent thinking
      context.become(countingMeals(tableMap))   // GOTO countingMeals
  }

  def countingMeals(tableMap: Map[ActorRef, Int]): Receive = {
    case MealEaten =>
      if(tableMap(sender()) < this.maxMeals) {
        val meals = tableMap(sender()) + 1
        val newTableMap = tableMap + (sender() -> meals)  // Update map with meal count

        log.info(s"SERVED ${newTableMap(sender())} MEALS TO ${sender().path.name}")

        sender() ! ContinueEating   // Ok to keep eating
        context.become(countingMeals(newTableMap))  // Send the new map back to the same state (RECURSIVE)
        checkTables(newTableMap)   // Check how many meals served
      } else  {
        sender() ! PoisonPill   // philosopher is done eating
        checkTables(tableMap)   // handles any messages left in the managers queue
      }
  }
}