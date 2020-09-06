
# Message Library IntelliJ Plugin

Experimentation in writing an IntelliJ plugin for an hypothetical message library.

## Core Concept

The message library use JSon Schema to dynamically define message data model.
Data is extracted from message using JSon Pointer defined as Java literal strings
and, as Java has no knowledge of JSon Pointer, the plugin helps to validate
the JSon Pointers by finding properties in JSon schema linked to message type.

## Example

A schema is liked to a message type by Java annotation:
```java
@SchemaFile("/MyType.json")
public class MyType implements MessageType {
}
```

Then data can be extracted from the message using JSon Pointer:
```java
public void process(Message<MyType> message) {
    System.out.println(message.getString("/address/mainStreet"));
}
``` 

## The Plugin

The plugin uses two IntelliJ feature to validate JSon Pointer:
* an indexer (`com.github.madbrain.jschema.ClassSchemaIndex`) to build an index from class name to linked schema
* an annotator (`com.github.madbrain.jschema.ExtractorSpecAnnotator`) to find calls to `Message.getString()` and validate the JSon pointer in the argument using the
  schema linked to the actual type of the the generic parameter.

## Usage

* Import project in IntelliJ
* Run graddle task `runIde`
* Use `example-project` as project in the runtime IDE

## TODO

* the code is extremely dirty: better use the availables APIs?
* write tests using [Annotator Test Tutorial](https://www.jetbrains.org/intellij/sdk/docs/tutorials/writing_tests_for_plugins/annotator_test.html)

## Links

This plugin is mostly based on:
* [IntelliJ Tutorial](https://jetbrains.org/intellij/sdk/docs/tutorials/custom_language_support/annotator.html)
* [UI Designer Form Binding](https://github.com/JetBrains/intellij-community/blob/68e88c3df8a21e0d3cf86ed1c79025a19885b48b/plugins/ui-designer/src/com/intellij/uiDesigner/binding/FormClassAnnotator.java)
