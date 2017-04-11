package diesel.implicits

import scalaz._
import scalaz.std.AllInstances._
import diesel.diesel
import org.scalatest.{FunSpec, Matchers}

import scala.language.higherKinds

/**
  * Created by Lloyd on 4/10/17.
  *
  * Copyright 2017
  */
class ImplicitsSpec extends FunSpec with Matchers {

  describe("composing languages using the implicits") {

    @diesel
    trait Maths[G[_]] {
      def int(i: Int): G[Int]
      def add(l: G[Int], r: G[Int]): G[Int]
    }

    @diesel
    trait Applicative[F[_]] {
      def map2[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C]
      def pure[A](a: A): F[A]
    }

    @diesel
    trait Logging[F[_]] {
      def info(s: String): F[Unit]
      def warn(s: String): F[Unit]
      def error(s: String): F[Unit]
    }

    import Maths._
    import Applicative._
    import scalaz.Scalaz._

    // Our combined algebra type
    type PRG[A[_]] = Applicative.Algebra[A] with Maths.Algebra[A] with Logging.Algebra[A]

    val monadicPlusOp = { (a: Int, b: Int, c: Int) =>
      import monadicplus._
      for {
        i <- add(int(a), int(b)).withAlg[PRG]
        if i > 3
        j <- pure(c).withAlg[PRG]
        _ <- Logging.info(j.toString).withAlg[PRG]
        k <- add(int(i), int(j)).withAlg[PRG]
      } yield k
    }

    val monadicOp = { (a: Int, b: Int, c: Int) =>
      import monadic._
      for {
        i <- add(int(a), int(b)).withAlg[PRG]
        j <- pure(c).withAlg[PRG]
        _ <- Logging.info(j.toString).withAlg[PRG]
        k <- add(int(i), int(j)).withAlg[PRG]
      } yield k
    }

    implicit def interp[F[_]](implicit F: Monad[F]) =
      new Applicative.Algebra[F] with Maths.Algebra[F] with Logging.Algebra[F] {
        def int(i: Int) = F.pure(i)
        def add(l: F[Int], r: F[Int]) =
          for {
            x <- l
            y <- r
          } yield x + y

        def map2[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C] =
          F.apply2(fa, fb)(f)
        def pure[A](a: A): F[A] = F.pure(a)
        def info(s: String)     = F.pure(println(s"INFO: $s"))
        def warn(s: String)     = F.pure(println(s"WARN: $s"))
        def error(s: String)    = F.pure(println(s"ERROR: $s"))
      }

    describe("using monadic") {

      it("should work") {
        val program1 = monadicOp(1, 2, 3)
        val program2 = monadicOp(3, 4, 5)
        program1[Option] shouldBe Some(6)
        program1[List] shouldBe List(6)
        program2[Option] shouldBe Some(12)
        program2[List] shouldBe List(12)
      }
    }

    describe("using monadicplus") {

      it("should work") {
        val program1 = monadicPlusOp(1, 2, 3)
        val program2 = monadicPlusOp(3, 4, 5)
        program1[Option] shouldBe None
        program1[List] shouldBe Nil
        program2[Option] shouldBe Some(12)
        program2[List] shouldBe List(12)
      }
    }

  }
}
