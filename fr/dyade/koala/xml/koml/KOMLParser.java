/*
 * Copyright (c) 1998 by Groupe Bull. All Rights Reserved
 * KOMLParser.java
 * $Id: KOMLParser.java,v 1.17 1998/12/14 10:38:25 plehegar Exp $
 */
package fr.dyade.koala.xml.koml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;

import org.xml.sax.AttributeList;
import org.xml.sax.DocumentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;

import fr.dyade.koala.serialization.ClassDescription;
import fr.dyade.koala.serialization.Field;
import fr.dyade.koala.serialization.ObjectOutputHandler;
import fr.dyade.koala.serialization.TypeFactory;
import fr.dyade.koala.util.Base64Decoder;
import fr.dyade.koala.util.ByteInputOutputStream;
import fr.dyade.koala.xml.sax.ParserFactory;

/**
 * @version $Revision: 1.17 $
 * @author  Philippe Le H�garet
 */
class KOMLParser implements KOMLConstants, DocumentHandler {

    Parser parser;

    // the handler
    private ObjectOutputHandler ObjectOuputHandler;

    // stack for states
    private int[]  stStack     = new int[100];
    private int    currentSt   = 0;

    // stack for id and ClassDescription reference
    private Pair[] stReference      = new Pair[100];
    private int    currentReference = 0;


    // minor version of this KOML
    private int minor = 0;

    private static final boolean KoalaDebug = false;

    // the current type of the <value>
    private int          valueType;

    // is the value is transient ?
    private boolean      isTransient;
    // the internal buffer to get the value of the <value>
    private StringBuffer buffer;

    // the current size of a <row>
    private int          rowSize;

    private ByteInputOutputStream   byteBuffer;

    // current class description
    private ClassDescription currentClass;
    private Hashtable       classes;

    /**
     * Creates a new KOMLParser.
     * Use the system property org.xml.sax.parser to change the parser.
     *
     * @exception KOMLException Can't create a parser.
     */
    KOMLParser() throws KOMLException {
        try {
            parser = ParserFactory.makeParser();
            parser.setDocumentHandler(this);
        } catch (Exception e) {
            e.printStackTrace();
            throw new KOMLException("can't create SAX parser ");
        }
	buffer = new StringBuffer();
	byteBuffer = new ByteInputOutputStream(128);
	classes = new Hashtable();
    }

    /**
     * Set the ObjectOuputHandler
     *
     * @param ObjectOuputHandler the new handler
     */
    public void setObjectOuputHandler(ObjectOutputHandler ObjectOuputHandler) {
        this.ObjectOuputHandler = ObjectOuputHandler;
    }

    /**
     * Returns the current ObjectOuputHandler
     *
     * @return the handler
     */
    protected ObjectOutputHandler getObjectOuputHandler() {
        return ObjectOuputHandler;
    }

    /**
     * Creates a new KOMLParser.
     * Use the system property org.xml.sax.parser to change the parser.
     *
     * @param in an XML inputstream
     */
    public void read(InputStream in) 
	    throws SAXException, IOException {
	getObjectOuputHandler().writeStartDocument();

	parser.parse(new InputSource(in));
	getObjectOuputHandler().writeEndDocument();
    }

    public void startDocument() throws SAXException {
        if (KoalaDebug) {
            System.err.println("** start document **");
        }
    }

    public void endDocument() throws SAXException {
        if (KoalaDebug) {
            System.err.println("** end document **");
        }
    }

    public void startElement(String name, AttributeList atts) 
            throws SAXException {       
        if (KoalaDebug) {
            System.err.println("** element ** " + name);
        }
	int h_name = name.hashCode();
	if (h_name == H_KOML) {
	    pushState(H_KOML);
	    checkVersion(atts.getValue(A_VERSION));
	} else if (!isEmptyState()) {
	    if (h_name == H_CLASSES) {
		pushState(H_CLASSES);
	    } else if (h_name == H_OBJECT 
		       || h_name == H_ARRAY
		       || h_name == H_OBJECT_CLASS) {
		handleArrayOrObject(h_name, atts);
	    } else if (h_name == H_VALUE) {
		handleValue(h_name, atts);
	    } else if (h_name == H_NULL) {
		pushState(H_NULL);
		try {
		    getObjectOuputHandler().writeNullReference(null);
		} catch (IOException e) {
		    throw new KOMLException("internal error, sorry", e);
		}
	    } else {
		int _state = getState();
		if (_state == H_CLASSES) {
		    if (h_name == H_CLASS) {
			pushState(H_CLASS);
			String nameC = atts.getValue(A_NAME);
			String _super = atts.getValue(A_SUPER);
			String t = atts.getValue(A_UID);
			long uid = 0;
			if (t != null) {
			    try {
				uid = Long.parseLong(t);
			    } catch (NumberFormatException e) {
				throw new KOMLException("invalid document", e);
			    }
			}
			if (classes.get(nameC) != null) {
			    throw new KOMLException("double definition of "
						    + "a class");
			}
			currentClass = new ClassDescription(nameC);
			currentClass.setType(TypeFactory.createType(nameC));
			if (V_TRUE.equals(atts.getValue(A_WRITEMETHOD))) {
			    currentClass.setHasWriteMethod();
			}
			currentClass.setSerialVersionUID(uid);
			if (_super != null) {
			    currentClass.setSuperClass((ClassDescription) 
						       classes.get(_super));
			}
			t = atts.getValue(A_IMPLEMENTS);
			if ((t != null) && (t.equals(V_EXTERNALIZABLE))) {
			    currentClass.setIsExternalizable();
			} else {
			    currentClass.setIsSerializable();
			}
		    } else {
			throw new KOMLException("invalid document");
		    }
		} else if (_state == H_SUPER || _state == H_OBJECT 
			   || _state == H_ARRAY || _state == H_OBJECT_CLASS) {
		    if (h_name == H_OBJECT 
			|| h_name == H_ARRAY 
			|| h_name == H_OBJECT_CLASS) {
			handleArrayOrObject(h_name, atts);
		    } else if (h_name == H_SUPER) {
			pushState(H_SUPER);
			ClassDescription _class = (ClassDescription)
			    classes.get(atts.getValue(A_CLASS));
			if (_class == null) {
			    throw new KOMLException("invalid document");
			}
			try {			    
			    pushReference(new Pair(0, _class));

			    getObjectOuputHandler().writeStartSuper(_class);
			} catch (IOException e) {
			    throw new KOMLException("internal error, sorry", 
						    e);
			}
		    } else if (h_name == H_REFERENCE) {
			pushState(H_REFERENCE);			
			String t = atts.getValue(A_REF);
			if (t != null) {
			    try {
				int j = Integer.parseInt(t.substring(1), 10);
				getObjectOuputHandler().writeReference(j, 
								       null);
			    } catch (NumberFormatException e) {
				throw new KOMLException("invalid document", e);
			    } catch (IOException e) {
				throw new KOMLException("internal error sorry",
							e);
			    }
			}
		    } else if (h_name == H_NULL) {
			pushState(H_NULL);

			try {
			    getObjectOuputHandler().writeNullReference(null);
			} catch (IOException e) {
			    throw new KOMLException("internal error, sorry",
						    e);
			}
		    } else if (h_name == H_VALUE) {
			pushState(H_VALUE);
			String t = atts.getValue(A_TYPE);
			
			if (t != null) {
			    valueType = t.hashCode();
			}
			isTransient = 
			    V_TRUE.equals(atts.getValue(A_TRANSIENT));
		    } else if (h_name == H_ROW) {
			pushState(H_ROW);			
			String t = atts.getValue(A_SIZE);
			if (t != null) {
			    try {
				rowSize = Integer.parseInt(t, 10);
			    } catch (NumberFormatException e) {
				throw new KOMLException("invalid document", e);
			    }
			}
		    } else {
			throw new KOMLException("invalid document");
		    }
		} else if (_state == H_CLASS) {
		    if (h_name == H_FIELD) {
			pushState(H_FIELD);
			String type = atts.getValue(A_TYPE);
			String __name = atts.getValue(A_NAME);
			if (type == null || (__name == null)) {
			    throw new KOMLException("invalid document");
			}
			try {
			    currentClass
				.addField(new Field(TypeFactory
						    .createType(type),
						    __name));
			} catch (Exception e) {
			    throw new KOMLException("invalid type " + type, e);
			}
		    } else {
			throw new KOMLException("invalid document");
		    }
		} else {
		    throw new KOMLException("invalid document");
		}
	    }
	} else {
	    throw new KOMLException("invalid document");
	}
    }

    private String convertProtected(StringBuffer sb) throws KOMLException {
	char[] t = new char[4];
	char[] buf = new char[sb.length()];
	int current = 0;

	for (int i = 0; i < sb.length(); i++) {
	    char c = sb.charAt(i);
	    
	    if (c == '\\') {
		c = sb.charAt(++i);
		switch (c) {
		case '\\':
		    buf[current++] = '\\';
		    break;
		case 't':
		    buf[current++] = '\t';
		    break;
		case 'n':
		    buf[current++] = '\n';
		    break;
		case 'r':
		    buf[current++] = '\r';
		    break;
		case 'b':
		    buf[current++] = '\b';
		    break;
		case 'f':
		    buf[current++] = '\f';
		    break;
		case '\'': case '\"':
		    buf[current++] = c;
		    break;
		case '0': case '1': case '2': case '3': 
		case '4': case '5': case '6': case '7':
		    t[0] = c;
		    sb.getChars(i, i+2, t, 1);
		    i += 1;
		    try {
			buf[current++] = 
			    (char) Integer.parseInt(new String(t, 0, 3),
						    8);
		    } catch (NumberFormatException e) {
			if (KoalaDebug) {
			    e.printStackTrace();
			}
			throw new KOMLException("invalid escaped character", e);
		    }
		    break;
		case 'u':
		    c = sb.charAt(++i);
		    i++;
		    t[0] = c;
		    sb.getChars(i, i+3, t, 1);
		    i += 2;
		    try {
			buf[current++] = 
			    (char) Integer.parseInt(new String(t, 0, 4),
						    16);
		    } catch (NumberFormatException e) {
			if (KoalaDebug) {
			    e.printStackTrace();
			}
			throw new KOMLException("invalid escaped character", e);
		    }
		    break;
		default:
		    throw new KOMLException("invalid escaped character " + c);
		}
	    } else {
		buf[current++] = c;
	    }
	}
	return new String(buf, 0, current);
    }
	    
    public void endElement(String name) throws SAXException {   
        if (KoalaDebug) {
            System.err.println("end element " + name);
        }
	int h_name = name.hashCode();

	popState(h_name);
	if (h_name == H_VALUE) {
	    String value = convertProtected(buffer);

	    try {
		if (valueType == H_V_BYTE) {
		    getObjectOuputHandler().write(Byte.parseByte(value.trim(), 10),
						  isTransient, null);
		} else if (valueType == H_V_SHORT) {
		    getObjectOuputHandler().write(Short.parseShort(value.trim(), 10),
						  isTransient, null);
		} else if (valueType == H_V_INT) {
		    getObjectOuputHandler().write(Integer.parseInt(value.trim(), 10),
						  isTransient, null);
		} else if (valueType == H_V_LONG) {
		    getObjectOuputHandler().write(Long.parseLong(value.trim()),
						  isTransient, null);
		} else if (valueType == H_V_FLOAT) {
		    getObjectOuputHandler()
			.write(Float.valueOf(value.trim()).floatValue(),
			       isTransient, null);
		} else if (valueType == H_V_DOUBLE) {
		    getObjectOuputHandler()
			.write(Double.valueOf(value.trim()).doubleValue(), 
			       isTransient, null);
		} else if (valueType == H_V_CHAR) {
		    getObjectOuputHandler().write(value.charAt(0), 
						  isTransient, null);
		} else if (valueType == H_V_BOOLEAN) {
		    getObjectOuputHandler().write(V_TRUE.equals(value.trim()), 
						  isTransient, null);
		} else if (valueType == H_V_STRING) {
		    getObjectOuputHandler().write(value, isTransient, null);
		} else {
		    throw new KOMLException("invalid type in value " + value);
		}
	    } catch (IOException e) {
		throw new KOMLException("internal error, sorry", e);
	    }	    
	    buffer.setLength(0);
	    valueType = 0;		
	} else if (h_name == H_ROW) {
	    try {
		byte[] t = byteBuffer.getInternalArrayByte();
		if (minor >= 2) {
		    ByteInputOutputStream out = new ByteInputOutputStream();
		    Base64Decoder.decode(out, t, 0, t.length);
		    t = out.getInternalArrayByte();
		}
		getObjectOuputHandler().writeRow(t, 0, rowSize);
	    } catch (IOException e) {
		throw new KOMLException("internal error, sorry", e);
	    }	    
	    byteBuffer.reset();
	    rowSize = -1;
	} else if (h_name ==  H_CLASS) {
	    classes.put(currentClass.getName(), currentClass);
	    try {
		getObjectOuputHandler().writeClassDescription(currentClass);
	    } catch (IOException e) {
		throw new KOMLException("internal error, sorry", e);
	    }
	} else if (h_name == H_OBJECT_CLASS) {	    
	    try {
		Pair p = popReference();
		getObjectOuputHandler().writeEndObjectClass(p.id, p._class, 
							    null);
	    } catch (IOException e) {
		throw new KOMLException("internal error, sorry", e);
	    }
	} else if (h_name == H_OBJECT) {	    
	    try {
		Pair p = popReference();
		getObjectOuputHandler().writeEndObject(p.id, p._class, null);
	    } catch (IOException e) {
		throw new KOMLException("internal error, sorry", e);
	    }
	} else if (h_name == H_ARRAY) {	    
	    try {
		Pair p = popReference();
		getObjectOuputHandler().writeEndArray(p.id, p._class, null);
	    } catch (IOException e) {
		throw new KOMLException("internal error, sorry", e);
	    }
	} else if (h_name == H_SUPER) {	    
	    try {
		Pair p = popReference();
		getObjectOuputHandler().writeEndSuper(p._class);
	    } catch (IOException e) {
		throw new KOMLException("internal error, sorry", e);
	    }
	}
    }

    // unused functions from SAX

    public void setDocumentLocator(Locator locator) {
        // nothing to do with this
    }

    public void characters(char ch[],
                           int start,
                           int length) throws SAXException {
	int _state = getState();

	if (_state == H_VALUE) {
	    buffer.append(ch, start, length);
	} else if (_state == H_ROW) {
	    int c = start + length;
	    for (int i = start; i < c; i++) {
		byteBuffer.write(ch[i]);
	    }
	}
    }

    public void ignorableWhitespace(char ch[],
                                    int start,
                                    int length) throws SAXException {
        // nothing to do with this
    }

    public void processingInstruction(String target,
                                      String data) throws SAXException {
        // nothing to do with this
    }                                        

    private boolean isEmptyState() {
	return (currentSt == 0);
    }

    private int getState() {
	return stStack[currentSt - 1];
    }

    private void handleValue(int h_name, AttributeList atts) 
	    throws SAXException {
	pushState(H_VALUE);
	String t = atts.getValue(A_TYPE);
	
	if (t != null) {
	    valueType = t.hashCode();
	}

	isTransient = V_TRUE.equals(atts.getValue(A_TRANSIENT));
    }

    private void handleArrayOrObject(int h_name, AttributeList atts) 
	    throws SAXException {
	int id = 0;
	
	String t = atts.getValue(A_ID);
	if (t != null) {
	    try {
		id = Integer.parseInt(t.substring(1), 10);
	    } catch (NumberFormatException e) {
		throw new KOMLException("invalid document");
	    }
	}
	ClassDescription _class = 
	    (ClassDescription) classes.get(atts.getValue(A_CLASS));

	if (_class == null) {
	    throw new KOMLException("Unknown class " + atts.getValue(A_CLASS));
	}

	pushReference(new Pair(id, _class));

	isTransient = V_TRUE.equals(atts.getValue(A_TRANSIENT));
	if (h_name == H_OBJECT) {
	    pushState(H_OBJECT);
	    if (!_class.getType().isObject()) {
		throw new KOMLException("invalid object type " 
					+ _class.getType());
	    }
	    try {
		getObjectOuputHandler().writeStartObject(id, _class,
							 isTransient, null);
	    } catch (IOException e) {
		throw new KOMLException("internal error, sorry", e);
	    }
	} else if (h_name == H_ARRAY) {
	    int size = 0;
	    pushState(H_ARRAY);
	    t = atts.getValue(A_LENGTH);		
	    if (t != null) {
		try {
		    size = Integer.parseInt(t, 10);
		} catch (NumberFormatException e) {
		    throw new KOMLException("invalid document");
		}
	    }
	    if (!_class.getType().isArray()) {
		throw new KOMLException("invalid array type " 
					+ _class.getType());
	    }
	    try {
		getObjectOuputHandler().writeStartArray(id, _class, size,
							isTransient, null);
	    } catch (IOException e) {
		throw new KOMLException("internal error, sorry", e);
	    }
	} else {
	    pushState(H_OBJECT_CLASS);
	    if (!_class.getType().isObject()
		&& !_class.getType().isString()) {
		throw new KOMLException("invalid object type " 
					+ _class.getType());
	    }
	    try {
		getObjectOuputHandler().writeStartObjectClass(id, _class,
							      isTransient,
							      null);
	    } catch (IOException e) {
		throw new KOMLException("internal error, sorry", e);
	    }
	}
    }

    private void pushState(int state) {
        if (currentSt == stStack.length) {
            int[] old = stStack;
            stStack = new int[old.length + old.length];
            System.arraycopy(old, 0, stStack, 0, currentSt);
        }
        stStack[currentSt++] = state;
    }

    private void popState(int state) throws KOMLException {
        if (currentSt == 0) {       
            throw new KOMLException("no state " + state);
        } else {
            if (stStack[currentSt - 1] == state) {
                currentSt--;
            } else {
                throw new KOMLException("invalid state " + stStack[currentSt-1]
                                        + " " + state);
            }
        }
    }

    private void pushReference(Pair pair) {
        if (currentReference == stReference.length) {
            Pair[] old = stReference;
            stReference = new Pair[old.length + old.length];
            System.arraycopy(old, 0, stReference, 0, currentReference);
        }
        stReference[currentReference++] = pair;
    }

    private Pair popReference() throws KOMLException {
        if (currentReference == 0) {       
            throw new KOMLException("no reference available ");
        } else {
            return stReference[--currentReference];
        }
    }

    private void checkVersion(String version) throws KOMLException {
	if (OLD_VERSION.equals(version)) {
	    // old format
	    // read it
	} else {
	    try {
		int index = version.indexOf('.');
		if (index != -1) {
		    int major = Integer.parseInt(version.substring(0,
								   index));
		    minor = Integer.parseInt(version.substring(index+1));
		    if (major > MAJOR_VERSION) {
			throw new Exception();
		    }
		} else {
		    throw new Exception();
		}
	    } catch (Exception e) {
		throw new KOMLException("Invalid version "
					+ version + " <> " 
					+ MAJOR_VERSION + "." 
					+ MINOR_VERSION);
	    }
	}
    }

}


class Pair {
    int id;
    ClassDescription _class;
    Pair(int id, ClassDescription _class) {
	this.id = id;
	this._class = _class;
    }
}
