## xls-modeler
### description
This library leveraging annotation processor for generating read from and write to excel file. the main advantages of this library, the column order can be determined on runtime.

### how to use
annotate the model class with @com.rabbani.annotation.Xls, and only accessible field(one has public modifier), non static,non transient, and non final.

```java

import com.rabbani.xls.annotation.Serialize;

import java.time.LocalDateTime;

@com.rabbani.annotation.Xls(caseSensitive=false,value={"no","date","timestamp"})
public class Document {
    //this will be included
    public String no;

    //this will not be included
    public final String date;

    //the following field will be used on the following snippet code
    @Col(serializer = @Serialize(param = "yyyy-MM-dd HH:mm:ss", value = com.document.tester.Serializer.class))
    public LocalDateTime timestamp;
}
```
accepted type for the field are all primitive along with its boxing type, and java.lang.String.
to use other type, one have to provides converter by annotated the field with @com.rabbani.annotation.Col and set serializer(read) and deserializer(write)

```java
package com.document.tester;

import org.apache.poi.ss.usermodel.Cell;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class Serializer implements com.rabbani.xls.engine.Serializer<LocalDateTime>, com.rabbani.xls.engine.Deserializer<LocalDateTime> {
    //param will be populated based on provided value on Deserializer.param() or Serializer.param
    private final DateTimeFormatter formatter;

    public DerSer(String param) {
        super(param);
        formatter = DateTimeFormatter.ofPattern(param);
    }

    @Override
    public void serialize(LocalDateTime value, Cell cell) {
        cell.setCellValue(Date.from(Instant.from(value.atZone(ZoneId.systemDefault()))));
    }

    @Override
    public LocalDateTime deserialize(Cell cell) {
        Date date = cell.getDateCellValue();
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

}
```
for getting the mapper, call com.rabbani.xls.engine.MapperService.getMapper(Class<?> clz) by passing mapped class(class annotated with @Xls)

```java
import com.rabbani.xls.engine.Mapper;
import com.rabbani.xls.engine.MapperService;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;

import javax.swing.text.Document;
import java.util.ArrayList;

private Mapper.Instance<Document> staticMapper = MapperService.getInstance().getMapper(Document.class).mapper();

private Mapper<Document> dynamicMapper = MapperService.getInstance().getMapper(Document.class);

private final String[] columnDocuments = {"no","date","timestamp"};

List<Document> getBasedOnPredefinedColumnPosition(Sheet sheet) {
    List<Document> mappedDocuments = new ArrayList<>();
    for (Row row : sheet) {
        mappedDocuments.add(staticMapper.read(row, null));
    }
    return mappedDocuments;
}

List<Document> getBasedOnCellValueName(Sheet sheet) {
    List<Document> mappedDocuments = new ArrayList<>();
    //this will generate mapper with column position based on the given cell header content
    Mapper.Instance<Document> mapper = dynamicMapper.mapper(sheet.getRow(0));
    for (Row row : sheet) {
        mappedDocuments.add(staticMapper.read(row, null));
    }
    return mappedDocuments;
}

void writeTo(Sheet sheet, Collection<? extends Document> documents) {
    int rowIndex = 0;
    Row row = sheet.createRow(rowIndex);
    
    for(int i = 0;i < columnDocuments.length;i++){
        Cell cell = row.createCell(i);
        cell.setCellValue(columnDocuments[i]);
    }
    
    Mapper.Instance<Document> mappedWriterBasedOnColumns = dynamicMapper.mapper(columnDocuments);
    
    for (Document document : documents) {
        row = sheet.createRow(rowIndex++);
        mappedWriterBasedOnColumns.write(row,null);
    }
}

```
