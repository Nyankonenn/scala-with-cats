package regexp

object ReifiedCps {

  enum Call {
    case Loop(regexp: Regexp, index: Int, continuation: Continuation)
    case Continue(index: Option[Int], continuation: Continuation)
    case Done(index: Option[Int])
  }

  enum Continuation {
    case AppendK(right: Regexp, next: Continuation)
    case OrElseK(second: Regexp, index: Int, next: Continuation)
    case RepeatK(regexp: Regexp, index: Int, next: Continuation)
    case DoneK

    def apply(idx: Option[Int]): Call =
      this match {
        case AppendK(right, next) =>
          idx match {
            case None    => Call.Continue(None, next)
            case Some(i) => Call.Loop(right, i, next)
          }

        case OrElseK(second, index, next) =>
          idx match {
            case None => Call.Loop(second, index, next)
            case some => Call.Continue(some, next)
          }

        case RepeatK(regexp, index, next) =>
          idx match {
            case None    => Call.Continue(Some(index), next)
            case Some(i) => Call.Loop(regexp, i, next)
          }

        case DoneK =>
          Call.Done(idx)
      }

  }

  enum Regexp extends regexp.Regexp[Regexp] {
    import Continuation.{AppendK, OrElseK, RepeatK, DoneK}

    def ++(that: Regexp): Regexp =
      Append(this, that)

    def orElse(that: Regexp): Regexp =
      OrElse(this, that)

    def repeat: Regexp =
      Repeat(this)

    def `*` : Regexp = this.repeat

    def matches(input: String): Boolean = {
      def loop(
          regexp: Regexp,
          idx: Int,
          cont: Continuation
      ): Call =
        regexp match {
          case Append(left, right) =>
            val k: Continuation = AppendK(right, cont)
            Call.Loop(left, idx, k)

          case OrElse(first, second) =>
            val k: Continuation = OrElseK(second, idx, cont)
            Call.Loop(first, idx, k)

          case Repeat(source) =>
            val k: Continuation = RepeatK(regexp, idx, cont)
            Call.Loop(source, idx, k)

          case Apply(string) =>
            Call.Continue(
              Option.when(input.startsWith(string, idx))(idx + string.size),
              cont
            )

          case Empty =>
            Call.Continue(None, cont)
        }

      def trampoline(next: Call): Option[Int] =
        next match {
          case Call.Loop(regexp, index, continuation) =>
            trampoline(loop(regexp, index, continuation))
          case Call.Continue(index, continuation) =>
            trampoline(continuation(index))
          case Call.Done(index) => index
        }

      // Check we matched the entire input
      trampoline(loop(this, 0, DoneK))
        .map(idx => idx == input.size)
        .getOrElse(false)
    }

    case Append(left: Regexp, right: Regexp)
    case OrElse(first: Regexp, second: Regexp)
    case Repeat(source: Regexp)
    case Apply(string: String)
    case Empty
  }
  object Regexp extends regexp.RegexpConstructors[Regexp] {
    val empty: Regexp = Empty

    def apply(string: String): Regexp =
      Apply(string)
  }
}
