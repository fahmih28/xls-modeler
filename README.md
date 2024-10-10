## xls-modeler
### description
This library leveraging annotation processor for generating read from and write to excel file. the main advantages of this library, the column order can be determined on runtime.

### usage
annotate the model class with @com.rabbani.annotation.Xls, and only accessible field(one has public modifier), non static,non transient, and non final.

```java

import com.rabbani.xls.annotation.Serializer;

import java.time.LocalDateTime;

@com.rabbani.annotation.Xls
public class Document {
    //this will be included
    public String no;

    //this will not be included
    public final String date;

    //the following field will be used on the following snippet code
    @Col(serializer = @Serializer(param = "yyyy-MM-dd HH:mm:ss",value = com.document.tester.Serializer.class))
    public LocalDateTime timestamp;
}
```
accepted type for the field are all primitive along with its boxing type, and java.lang.String.
to use another type, one have to provides converter by annotated the field with @com.rabbani.annotation.Col and set serializer(read) and deserializer(write)

```java
package com.document.tester;

import org.apache.poi.ss.usermodel.Cell;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Serializer extends com.rabbani.xls.engine.DerSer<Document> {
    //param will be populated based on provided value on Deserializer.param() or Serializer.param
    private final DateTimeFormatter formatter;

    public DerSer(String param) {
        super(param);
        formatter = DateTimeFormatter.ofPattern(param);
    }

    @Override
    public void apply(Cell cell, String name, T value) {
        value.timestamp = formatter.parse(cell.getStringCellValue(), LocalDateTime::from);
    }
}
```
