java -cp dist/xmlpg.jar edu.nps.moves.xmlpg.Xmlpg DIS7.xml schema
echo "Generating csharp"
spatial schema generate --cachePath=.spatialos/schema_codegen_cache --output=build/GeneratedCode/csharp --language=csharp
echo "Generating java"
spatial schema generate --cachePath=.spatialos/schema_codegen_cache --output=build/GeneratedCode/java --language=java
echo "Generating cpp"
spatial schema generate --cachePath=.spatialos/schema_codegen_cache --output=build/GeneratedCode/cpp --language=cpp