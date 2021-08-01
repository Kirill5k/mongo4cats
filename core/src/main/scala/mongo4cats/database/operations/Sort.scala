package mongo4cats.database.operations

import com.mongodb.client.model.Sorts
import org.bson.conversions.Bson

import scala.jdk.CollectionConverters._

trait Sort {

  /** Create a sort specification for an ascending sort on the given field.
    *
    * @param fieldNames
    *   the field name
    * @return
    *   the sort specification [[https://docs.mongodb.com/manual/reference/method/cursor.sort/#std-label-sort-asc-desc]]
    */
  def asc(fieldNames: String*): Sort

  /** Create a sort specification for a descending sort on the given field.
    *
    * @param fieldNames
    *   the field name
    * @return
    *   the sort specification [[https://docs.mongodb.com/manual/reference/method/cursor.sort/#std-label-sort-asc-desc]]
    */
  def desc(fieldNames: String*): Sort

  /** Create a sort specification for the text score meta projection on the given field.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the sort specification [[https://docs.mongodb.com/manual/reference/method/cursor.sort/#std-label-sort-metadata]]
    */
  def metaTextScore(fieldName: String): Sort

  /** Combine multiple sort specifications. If any field names are repeated, the last one takes precedence.
    *
    * @param anotherSort
    *   the sort specifications
    * @return
    *   the combined sort specification
    */
  def combinedWith(anotherSort: Sort): Sort

  private[database] def toBson: Bson
  private[operations] def sorts: List[Bson]
}

object Sort {
  private val empty: Sort = SortBuilder(Nil)

  def asc(fieldNames: String*): Sort         = empty.asc(fieldNames: _*)
  def desc(fieldNames: String*): Sort        = empty.desc(fieldNames: _*)
  def metaTextScore(fieldName: String): Sort = empty.metaTextScore(fieldName)
}

final private case class SortBuilder(
    override val sorts: List[Bson]
) extends Sort {

  override def asc(fieldNames: String*): Sort         = SortBuilder(Sorts.ascending(fieldNames: _*) :: sorts)
  override def desc(fieldNames: String*): Sort        = SortBuilder(Sorts.descending(fieldNames: _*) :: sorts)
  override def metaTextScore(fieldName: String): Sort = SortBuilder(Sorts.metaTextScore(fieldName) :: sorts)

  override def combinedWith(anotherSort: Sort): Sort =
    SortBuilder(anotherSort.sorts ::: sorts)

  override private[database] def toBson: Bson =
    Sorts.orderBy(sorts.reverse.asJava)
}
