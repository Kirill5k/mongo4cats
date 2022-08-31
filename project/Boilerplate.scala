import sbt._

/** Generate a range of boilerplate classes that would be tedious to write and maintain by hand.
  *
  * Copied, with some modifications, from:
  *   - [[https://github.com/milessabin/shapeless/blob/master/project/Boilerplate.scala Shapeless]].
  *   - [[https://github.com/circe/circe/blob/main/project/Boilerplate.scala]].
  *
  * @author
  *   Miles Sabin
  * @author
  *   Kevin Wright
  */
object Boilerplate {
  import scala.StringContext._

  implicit class BlockHelper(val sc: StringContext) extends AnyVal {
    def block(args: Any*): String = {
      val interpolated = sc.standardInterpolator(treatEscapes, args)
      val rawLines     = interpolated.split('\n')
      val trimmedLines = rawLines.map(_.dropWhile(_.isWhitespace))
      trimmedLines.mkString("\n")
    }
  }

  val templates: Seq[Template] = Seq(
    GenTupleDecoders,
    GenTupleEncoders
  )

  val header   = "// auto-generated boilerplate"
  val maxArity = 22

  /** Return a sequence of the generated files.
    *
    * As a side-effect, it actually generates them...
    */
  def gen(dir: File): Seq[File] = templates.map { template =>
    val tgtFile = template.filename(dir)
    IO.write(tgtFile, template.body)
    tgtFile
  }

  class TemplateVals(val arity: Int) {
    val synTypes = (0 until arity).map(n => s"A$n")
    val synVals  = (0 until arity).map(n => s"a$n")
    val `A..N`   = synTypes.mkString(", ")
    val `a..n`   = synVals.mkString(", ")
    val `_.._`   = Seq.fill(arity)("_").mkString(", ")
    val `(A..N)` = if (arity == 1) "Tuple1[A0]" else synTypes.mkString("(", ", ", ")")
    val `(_.._)` = if (arity == 1) "Tuple1[_]" else Seq.fill(arity)("_").mkString("(", ", ", ")")
    val `(a..n)` = if (arity == 1) "Tuple1(a)" else synVals.mkString("(", ", ", ")")
  }

  /** Blocks in the templates below use a custom interpolator, combined with post-processing to produce the body.
    *
    *   - The contents of the `header` val is output first
    *   - Then the first block of lines beginning with '|'
    *   - Then the block of lines beginning with '-' is replicated once for each arity, with the `templateVals` already pre-populated with
    *     relevant vals for that arity
    *   - Then the last block of lines prefixed with '|'
    *
    * The block otherwise behaves as a standard interpolated string with regards to variable substitution.
    */
  trait Template {
    def filename(root: File): File
    def content(tv: TemplateVals): String
    def range: IndexedSeq[Int] = 1 to maxArity
    def body: String = {
      val headerLines = header.split('\n')
      val raw         = range.map(n => content(new TemplateVals(n)).split('\n').filterNot(_.isEmpty))
      val preBody     = raw.head.takeWhile(_.startsWith("|")).map(_.tail)
      val instances   = raw.flatMap(_.filter(_.startsWith("-")).map(_.tail))
      val postBody    = raw.head.dropWhile(_.startsWith("|")).dropWhile(_.startsWith("-")).map(_.tail)
      (headerLines ++ preBody ++ instances ++ postBody).mkString("\n")
    }
  }

  object GenTupleDecoders extends Template {
    override def range: IndexedSeq[Int] = 1 to maxArity

    def filename(root: File): File = root / "mongo4cats" / "derivation" / "bson" / "TupleBsonDecoders"

    def content(tv: TemplateVals): String = {
      import tv._

      val instances = synTypes.map(tpe => s"dec$tpe: BsonDecoder[$tpe]").mkString(", ")
      val unsafeDecodeBody =
        synTypes.zipWithIndex
          .map { case (tpe, n) => s"reader.readBsonType(); val v$n = dec$tpe.unsafeDecode(reader, decoderContext); " }
          .mkString(
            "reader.readStartArray(); ",
            "",
            s"reader.readEndArray(); ${synTypes.indices.map("v" + _).mkString(s"Tuple$arity(", ", ", ")")}"
          )

      val unsafeFromBsonValueBody =
        synTypes.zipWithIndex
          .map { case (tpe, n) => s"dec$tpe.unsafeFromBsonValue(arr.get($n))" }
          .mkString(
            s"case arr: BsonArray if arr.size() == ${synTypes.size} => Tuple$arity(",
            ", ",
            s"""); case arr: BsonArray => throw new Throwable(s"Not an array of size ${synTypes.size}: $$arr"); case other => throw new Throwable(s"Not an array: $$other") """
          )

      block"""
        |package mongo4cats.derivation.bson
        |
        |import org.bson._
        |import org.bson.codecs._
        |
        |private[bson] trait TupleBsonDecoders {
        -  implicit def tuple${arity}BsonDecoder[${`A..N`}](implicit $instances): BsonDecoder[${`(A..N)`}] =
        -    new BsonDecoder[${`(A..N)`}] {
        -      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): ${`(A..N)`} = {
        -        $unsafeDecodeBody
        -      }
        -
        -      override def unsafeFromBsonValue(bson: BsonValue): ${`(A..N)`} = bson match {
        -        $unsafeFromBsonValueBody
        -      }
        -    }
        |}
      """
    }
  }

  object GenTupleEncoders extends Template {
    override def range: IndexedSeq[Int] = 1 to maxArity

    def filename(root: File): File = root / "mongo4cats" / "derivation" / "bson" / "TupleBsonEncoders"

    def content(tv: TemplateVals): String = {
      import tv._

      val instances = synTypes.map(tpe => s"enc$tpe: BsonEncoder[$tpe]").mkString(", ")
      val encodeBody = synTypes.zipWithIndex
        .map { case (tpe, n) => s"enc$tpe.unsafeBsonEncode(writer, value._${n + 1}, encoderContext)" }
        .mkString("; ")

      block"""
        |package mongo4cats.derivation.bson
        |
        |import org.bson._
        |import org.bson.codecs._
        |import mongo4cats.derivation.bson._
        |import mongo4cats.derivation.bson.BsonEncoder.instanceFromJavaCodec
        |
        |private[bson] trait TupleBsonEncoders {
        -  implicit def tuple${arity}BsonEncoder[${`A..N`}](implicit $instances): BsonEncoder[${`(A..N)`}] =
        -    instanceFromJavaCodec(new JavaEncoder[${`(A..N)`}] {
        -      override def encode(writer: BsonWriter, value: ${`(A..N)`}, encoderContext: EncoderContext): Unit = {
        -        writer.writeStartArray()
        -        $encodeBody
        -        writer.writeEndArray()
        -      }
        -    })
        |}
      """
    }
  }
}
