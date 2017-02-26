package com.doanduyhai.elevator.actors

import akka.actor.{Props, ActorSystem}
import akka.testkit.{EventFilter, TestProbe, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.collection.immutable.Queue


class ControlSystemActorTest extends TestKit(ActorSystem("ControlSystemActorSystem",
  ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"]"""))) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "ControlSystemActor" must  {

    "get the status of all current elevators" in {
      //Given
      val elevator1 = TestProbe()
      val elevator2 = TestProbe()
      val simulationActor = TestProbe()

      val elevators = Map(1 -> (elevator1.ref, AtFloor(0), None), 2 ->(elevator2.ref, Move(3, 0), None))
      val controlSystem = system.actorOf(Props(new ControlSystemActor(elevators, simulationActor.ref)))

      //When
      controlSystem ! GetElevatorStatus

      //Then
      simulationActor.expectMsg(ElevatorsStatuses(Map(1 -> (AtFloor(0), None), 2 -> (Move(3, 0), None))))
    }

    "forward the pickup order to a free elevator" in {
      //Given
      val elevator1 = TestProbe()
      val elevator2 = TestProbe()
      val simulationActor = TestProbe()

      val elevators = Map(1 -> (elevator1.ref, AtFloor(0), None), 2 ->(elevator2.ref, Move(3, 0), None))
      val controlSystem = system.actorOf(Props(new ControlSystemActor(elevators, simulationActor.ref)))

      //When
      controlSystem ! Pickup(Move(3,1))

      //Then
      elevator1.expectMsgAnyOf(Pickup(Move(3,1)))
    }

    "update status for an elevator" in {
      //Given
      val elevator1 = TestProbe()
      val elevator2 = TestProbe()
      val simulationActor = TestProbe()

      val elevators = Map(1 -> (elevator1.ref, AtFloor(0), None), 2 ->(elevator2.ref, Move(3, 0), None))
      val controlSystem = system.actorOf(Props(new ControlSystemActor(elevators, simulationActor.ref)))

      //When
      controlSystem ! UpdateStatus(1, Move(0, 4), None)

      //Then
      controlSystem ! GetElevatorStatus
      simulationActor.expectMsg(ElevatorsStatuses(Map(1 -> (Move(0, 4), None), 2 -> (Move(3, 0), None))))
    }

    "update scheduled order for an elevator" in {
      //Given
      val elevator1 = TestProbe()
      val elevator2 = TestProbe()
      val simulationActor = TestProbe()

      val elevators = Map(1 -> (elevator1.ref, Move(0, 4), None), 2 ->(elevator2.ref, Move(3, 0), None))
      val controlSystem = system.actorOf(Props(new ControlSystemActor(elevators, simulationActor.ref)))

      //When
      controlSystem ! UpdateStatus(1, Move(0, 4), Some(Pickup(Move(5,2))))

      //Then
      simulationActor.expectMsgAnyOf(ElevatorsStatuses(Map(1 -> (Move(0, 4), Some(Pickup(Move(5,2)))), 2 -> (Move(3, 0), None))))
    }

    "push a pickup order to an elevator which has no scheduled order" in {
      //Given
      val elevator1 = TestProbe()
      val elevator2 = TestProbe()
      val elevator3 = TestProbe()
      val simulationActor = TestProbe()

      val elevators = Map(
        1 -> (elevator1.ref, Move(0, 4), Some(Pickup(Move(5,2)))),
        2 ->(elevator2.ref, Move(3, 0), None),
        3 ->(elevator3.ref, Move(2, 3), Some(Pickup(Move(3,5)))))

      val controlSystem = system.actorOf(Props(new ControlSystemActor(elevators, simulationActor.ref)))

      //When
      controlSystem ! Pickup(Move(6,0))

      //Then
      elevator2.expectMsg(Pickup(Move(6,0)))
    }

    "enqueue a pickup order when nor free neither un-scheduled elevator" in {
      //Given
      val elevator1 = TestProbe()
      val elevator2 = TestProbe()
      val elevator3 = TestProbe()
      val simulationActor = TestProbe()

      val elevators = Map(
        1 -> (elevator1.ref, Move(0, 4), Some(Pickup(Move(5,2)))),
        2 ->(elevator2.ref, Move(3, 0), Some(Pickup(Move(0,2)))),
        3 ->(elevator3.ref, Move(2, 3), Some(Pickup(Move(3,5)))))

      val controlSystem = system.actorOf(Props(new ControlSystemActor(elevators, simulationActor.ref, Queue(Pickup(Move(5,6))))))

      //When
      controlSystem ! Pickup(Move(0,1))

      //Then
      controlSystem ! GetQueueStatus
      simulationActor.expectMsgAllOf(Queue(Pickup(Move(5,6)), Pickup(Move(0,1))))
    }

    "dequeue a pickup order when an elevator has unscheduled an order" in {
      //Given
      val elevator1 = TestProbe()
      val elevator2 = TestProbe()
      val elevator3 = TestProbe()
      val simulationActor = TestProbe()

      val elevators = Map(
        1 -> (elevator1.ref, Move(0, 4), Some(Pickup(Move(5,2)))),
        2 ->(elevator2.ref, Move(3, 0), Some(Pickup(Move(0,2)))),
        3 ->(elevator3.ref, Move(2, 3), Some(Pickup(Move(3,5)))))

      val controlSystem = system.actorOf(Props(new ControlSystemActor(elevators, simulationActor.ref,
        Queue(Pickup(Move(5,6)), Pickup(Move(3,4))))))

      //When
      controlSystem ! UpdateStatus(1, Move(0, 4), None)

      //Then
      elevator1.expectMsg(Pickup(Move(5,6)))

      controlSystem ! GetQueueStatus
      simulationActor.expectMsgAllOf(ElevatorsStatuses(Map(1 -> (Move(0,4),None), 2 -> (Move(3,0),Some(Pickup(Move(0,2)))), 3 -> (Move(2,3),Some(Pickup(Move(3,5)))))),
        Queue(Pickup(Move(3,4))))
    }

    "write error message and reject a pickup order when queue is full" in {
      //Given
      val elevator1 = TestProbe()
      val elevator2 = TestProbe()
      val elevator3 = TestProbe()
      val simulationActor = TestProbe()

      val elevators = Map(
        1 -> (elevator1.ref, Move(0, 4), Some(Pickup(Move(5,2)))),
        2 ->(elevator2.ref, Move(3, 0), Some(Pickup(Move(0,2)))),
        3 ->(elevator3.ref, Move(2, 3), Some(Pickup(Move(3,5)))))

      val controlSystem = system.actorOf(Props(new ControlSystemActor(elevators, simulationActor.ref,
        Queue(Pickup(Move(5,6))), maxQueueSize = 1)))

      //Then
      EventFilter.error(message = s"Cannot enqueue order Pickup(Move(1,7)) because the queue is full", occurrences = 1) intercept {
        controlSystem ! Pickup(Move(1,7))
      }
    }

    "send ExecuteSimulation command to all elevators" in {
      //Given
      val elevator1 = TestProbe()
      val elevator2 = TestProbe()
      val elevator3 = TestProbe()
      val simulationActor = TestProbe()

      val elevators = Map(
        1 -> (elevator1.ref, Move(0, 4), Some(Pickup(Move(5,2)))),
        2 ->(elevator2.ref, Move(3, 0), Some(Pickup(Move(0,2)))),
        3 ->(elevator3.ref, Move(2, 3), Some(Pickup(Move(3,5)))))

      val controlSystem = system.actorOf(Props(new ControlSystemActor(elevators, simulationActor.ref)))

      //When
      controlSystem ! StartSimulation

      //Then
      elevator1.expectMsg(StartSimulation)
      elevator2.expectMsg(StartSimulation)
      elevator3.expectMsg(StartSimulation)
    }
  }
}
