/* Copyright (c) 1998 by Groupe Bull. All Rights Reserved */
/* $Id: AWTListenerSerializer.java,v 1.5 1998/11/02 21:07:04 plehegar Exp $ */
/* Author: Thierry-Kormann@sophia.inria.fr  */

package fr.dyade.koala.serialization.api;

import java.io.IOException;
import fr.dyade.koala.serialization.*;

/**
 * This class contains the static methods needed to enable the XML
 * serialization of the listeners.
 *
 * @author Thierry.Kormann@sophia.inria.fr 
 */
public class AWTListenerSerializer {

    public static void readObject(GeneratorInputStream s)
	    throws ClassNotFoundException, IOException {
	s.defaultReadObject();
	while (null != s.readObject()) {
	    s.readObject();
	}
    }
}
