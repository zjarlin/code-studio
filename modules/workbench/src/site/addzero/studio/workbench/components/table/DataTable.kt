package site.addzero.studio.workbench.components.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import site.addzero.component.table.original.TableOriginal
import site.addzero.component.table.original.entity.ColumnConfig
import site.addzero.component.table.original.entity.TableLayoutConfig

internal class DataColumn<T>(
    val key: String,
    val label: String,
    val width: Float = 150f,
    val value: (T) -> String,
)

@Composable
internal fun <T> DataTable(
    data: List<T>,
    columns: List<DataColumn<T>>,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.(T, Int) -> Unit,
) {
    val indexedData = remember(data) { data.withIndex().toList() }
    val columnConfigs = remember(columns) {
        columns.mapIndexed { index, column ->
            ColumnConfig(
                key = column.key,
                comment = column.label,
                width = column.width,
                order = index,
                showFilter = false,
                showSort = false,
            )
        }
    }
    TableOriginal(
        data = indexedData,
        columns = columns,
        getColumnKey = DataColumn<T>::key,
        getRowId = IndexedValue<T>::index,
        columnConfigs = columnConfigs,
        layoutConfig = compactTableLayout,
        getColumnLabel = { column ->
            Text(column.label, fontWeight = FontWeight.SemiBold)
        },
        getCellText = { row, column -> column.value(row.value) },
        rowActionSlot = { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                actions(row.value, row.index)
            }
        },
        modifier = modifier,
    )
}

private val compactTableLayout = TableLayoutConfig(
    indexColumnWidthDp = 48f,
    actionColumnWidthDp = 88f,
    headerHeightDp = 40f,
    rowHeightDp = 42f,
)
