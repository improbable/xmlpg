package edu.nps.moves.xmlpg;

import java.io.*;
import java.util.*;

public class SchemaGenerator extends Generator
{
    /** Maps the primitive types listed in the XML file to the schema types */
    Properties types = new Properties();

    Properties aliases = new Properties();

    /** A property list that contains schema-specific code generation information, such
     * as package names, includes, etc.
     */
    Properties schemaProperties;

    public SchemaGenerator(HashMap pClassDescriptions, Properties pSchemaProperties)
    {
        super(pClassDescriptions, pSchemaProperties);

        Properties systemProperties = System.getProperties();
        String clPduOffset = systemProperties.getProperty("xmlpg.pduOffset");

        String clDirectory = systemProperties.getProperty("xmlpg.generatedSourceDir");

        try {
            if(clPduOffset != null && Integer.parseInt(clPduOffset) > 0)
                pSchemaProperties.setProperty("pduOffset", clPduOffset);
        }
        catch(NumberFormatException e) {
            System.out.println("PDU offset is not an integer. Modify the XML file to fix value.");
            System.out.println(e);
            System.exit(-1);
        }

        // Directory to place generated source code
        if(clDirectory != null)
            pSchemaProperties.setProperty("directory", clDirectory);

        super.setDirectory(pSchemaProperties.getProperty("directory"));

        // Set up a mapping between the strings used in the XML file and the strings used
        // in the java file, specifically the data types. This could be externalized to
        // a properties file, but there's only a dozen or so and an external props file
        // would just add some complexity.
        types.setProperty("unsigned short", "uint32");
        types.setProperty("unsigned byte", "uint32");
        types.setProperty("unsigned int", "uint32");
        types.setProperty("unsigned long", "uint64");

        types.setProperty("byte", "sint32");
        types.setProperty("short", "sint32");
        types.setProperty("int", "sint32");
        types.setProperty("long", "sint64");

        types.setProperty("double", "double");
        types.setProperty("float", "float");

        // Alias these types out of existence.
        aliases.setProperty("OneByteChunk", "byte");
        aliases.setProperty("TwoByteChunk", "uint32");
        aliases.setProperty("FourByteChunk", "uint32");
        aliases.setProperty("EightByteChunk", "uint64");
    }

    public String getType(String in) {
        String rv = aliases.getProperty(in);
        if (rv != null)
            in = rv;

        rv = types.getProperty(in);
        if (rv == null)
            rv = in;

        return rv;
    }

    /**
     * Generates the schema source code classes
     */
    public void writeClasses() {
        this.createDirectory();

        Iterator it = classDescriptions.values().iterator();

        while(it.hasNext()) {
            try {
                this.writeSchemaFile((GeneratedClass)it.next());
            }
            catch(Exception e) {
                System.out.println("error creating source code " + e);
            }
        }
    }

    /**
     * Generate a schema file for the classes
     */
    public void writeSchemaFile(GeneratedClass aClass)
    {
        if (aliases.getProperty(aClass.getName()) != null)
            return;

        try {
            String name = aClass.getName();
            String headerFullPath = getDirectory() + "/" + name + ".schema";
            File outputFile = new File(headerFullPath);
            outputFile.createNewFile();
            PrintWriter pw = new PrintWriter(outputFile);

            // Write includes for any classes we may reference. this generates multiple #includes if we
            // use a class multiple times, but that's innocuous. We could sort and do a unqiue to prevent
            // this if so inclined.

            pw.println("// Copyright (c) Improbable Worlds Ltd, All Rights Reserved");
            pw.println();

            String namespace = languageProperties.getProperty("namespace");
            // Print out namespace, if any
            if(namespace != null) {
                pw.println("package " + namespace.toLowerCase() + ";");
                namespace = namespace + "/";
            }
            else {
                namespace = "";
            }

            Set attribs = new HashSet<String>();
            for(int i = 0; i < aClass.getClassAttributes().size(); i++) {
                ClassAttribute anAttribute = (ClassAttribute)aClass.getClassAttributes().get(i);

                if (attribs.contains(anAttribute.getType()))
                    continue;

                if (aliases.getProperty(anAttribute.getType()) != null)
                    continue;

                // If this attribute is a class, we need to do an import on that class
                if(anAttribute.getAttributeKind() == ClassAttribute.ClassAttributeType.CLASSREF)
                    pw.println("import \"" + namespace + anAttribute.getType() + ".schema\";");

                // if this attribute is a variable-length list that holds a class, we need to
                // do an import on the class that is in the list.
                if(anAttribute.getAttributeKind() == ClassAttribute.ClassAttributeType.VARIABLE_LIST)
                    pw.println("import \"" + namespace + anAttribute.getType() + ".schema\";");

                attribs.add(anAttribute.getType());
            }

            // if we inherit from another class we need to do an include on it
            if (!aClass.getParentClass().isEmpty() &&
                    aliases.getProperty(aClass.getParentClass()) == null &&
                    !aClass.getParentClass().equalsIgnoreCase("root") &&
                    !aClass.getParentClass().equalsIgnoreCase("Pdu")) {
                pw.println("import \"" + namespace + aClass.getParentClass() + ".schema\";");
            }

            pw.println();

            // Print out the class comments, if any
            if(aClass.getClassComments() != null)
                pw.println("// " + aClass.getClassComments());

            int pdu = 0;
            for(int i = 0; i < aClass.getInitialValues().size(); i++) {
                InitialValue aValue = (InitialValue)aClass.getInitialValues().get(i);

                if (aValue.getVariable().equalsIgnoreCase("pduType")) {
                    pdu = Integer.parseInt(aValue.getVariableValue());
                    String pduOffset = languageProperties.getProperty("pduOffset");

                    if (pduOffset != null)
                        pdu += Integer.parseInt(pduOffset);
                    if(aClass.getName().equalsIgnoreCase("FastEntityStatePdu"))
                        pdu += 70;
                }
            }

            if (pdu > 0) {
                pw.println("component " + aClass.getName() + " {");
                pw.println("  id = " + pdu + ";");
                pw.println();
            } else {
                pw.println("type " + aClass.getName() + " {");
            }

            GeneratedClass parent = null;
            int id = 1;
            if (!aClass.getParentClass().isEmpty() &&
                !aClass.getParentClass().equalsIgnoreCase("root") &&
                !aClass.getParentClass().equalsIgnoreCase("Pdu")) {
                pw.println("  " + "/** Schema does not support inheritance, this is as close as we can get. */");
                pw.println("  " + aClass.getParentClass() + " super = " + id + ";");
                pw.println();
                id++;
            }

            for(int i = 0; i < aClass.getClassAttributes().size(); i++, id++) {
                ClassAttribute anAttribute = (ClassAttribute)aClass.getClassAttributes().get(i);

                if(anAttribute.getName().startsWith("pad") || anAttribute.getName().endsWith("Padding"))
                    continue;

                if(i > 0)
                    pw.println();

                if(anAttribute.getComment() != null)
                    pw.println("  " + "/** " + anAttribute.getComment() + " */");

                String type = getType(anAttribute.getType());
                if(anAttribute.getAttributeKind() == ClassAttribute.ClassAttributeType.PRIMITIVE ||
                   anAttribute.getAttributeKind() == ClassAttribute.ClassAttributeType.CLASSREF) {
                    pw.println("  " + type + " " + makeSnakeCase(anAttribute.getName()) + " = " + id + ";");
                }
                else if(anAttribute.getAttributeKind() == ClassAttribute.ClassAttributeType.FIXED_LIST ||
                        anAttribute.getAttributeKind() == ClassAttribute.ClassAttributeType.VARIABLE_LIST) {
                    String alias = aliases.getProperty(anAttribute.getType());
                    if ((alias != null && (alias.equals("byte") || alias.equals("unsigned byte"))) ||
                        anAttribute.getType().equals("byte") || anAttribute.getType().equals("unsigned byte")) {
                        if (anAttribute.getCouldBeString())
                            pw.println("  string " + makeSnakeCase(anAttribute.getName()) + " = " + id + ";");
                        else
                            pw.println("  bytes " + makeSnakeCase(anAttribute.getName()) + " = " + id + ";");
                    } else {
                        pw.println("  list<" + type + "> " + makeSnakeCase(anAttribute.getName()) + " = " + id + ";");
                    }
                }
            }

            pw.println("}");

            pw.flush();
            pw.close();
        }
        catch(Exception e)
        {
            System.out.println(e);
        }
    }

    static final String makeSnakeCase(String in) {
        return in.replaceAll("(.)(\\p{Upper})", "$1_$2").toLowerCase();
    }
}
