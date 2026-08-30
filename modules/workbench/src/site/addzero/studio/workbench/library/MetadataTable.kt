package site.addzero.studio.workbench.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class MetadataColumn(
    val key: String,
    val label: String,
    val width: Int = 150,
)

@Composable
internal fun <T> MetadataTable(
    data: List<T>,
    columns: List<MetadataColumn>,
    cell: (T, String) -> String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.(T, Int) -> Unit,
) {
    Surface(
        modifier = modifier,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp)) {
                columns.forEach { column ->
                    Text(column.label, modifier = Modifier.width(column.width.dp), fontWeight = FontWeight.SemiBold)
                }
                Text("操作", modifier = Modifier.width(96.dp), fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(data) { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        columns.forEach { column ->
                            Text(cell(item, column.key), modifier = Modifier.width(column.width.dp), maxLines = 1)
                        }
                        actions(item, index)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
