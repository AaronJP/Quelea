/* 
 * This file is part of Quelea, free projection software for churches.
 * Copyright (C) 2011 Michael Berry
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.quelea.powerpoint;

import com.sun.star.beans.PropertyValue;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.comp.helper.BootstrapException;
import com.sun.star.frame.XComponentLoader;
import com.sun.star.frame.XModel;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.lang.XComponent;
import com.sun.star.presentation.XPresentation;
import com.sun.star.presentation.XPresentation2;
import com.sun.star.presentation.XPresentationSupplier;
import com.sun.star.presentation.XSlideShowController;
import com.sun.star.sdbc.SQLException;
import com.sun.star.sdbc.XCloseable;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.quelea.utils.Utils;

/**
 * A presentation to be displayed using the openoffice API. This requries
 * openoffice to be installed.
 *
 * @author Michael
 */
public class OOPresentation {

    private static final Logger LOGGER = Logger.getLogger(OOPresentation.class.getName());
    private static XComponentContext xOfficeContext;
    private static boolean init;
    private XPresentation2 xPresentation;
    private XSlideShowController controller;
    private XComponent doc;
    private boolean disposed;

    /**
     * Initialise the library - this involves connecting to openoffice to
     * initialise the office context object (which is used to create
     * presentations.)
     *
     * @param ooPath the path to the the "program" folder inside the openoffice
     * directory.
     * @return true if successfully initialised, false if an error occurs.
     */
    public static boolean init(String ooPath) {
        try {
            xOfficeContext = Helper.connect(ooPath);
            init = true;
            return true;
        }
        catch (BootstrapException ex) {
            LOGGER.log(Level.SEVERE, "Couldn't connect to openoffice instance", ex);
            return false;
        }
    }

    /**
     * Determine if the library is initialised.
     *
     * @return true if it's already initialised, false otherwise.
     */
    public static boolean isInit() {
        return init;
    }

    /**
     * Create a new presentation from a particular file. This must be the path
     * to a presentation file that openoffice supports - so either odp, ppt or
     * pptx at the time of writing. Note that the static init() method on this
     * class must be called successfully before attempting to create any
     * presentation objects, otherwise an IllegalStateException will be thrown.
     *
     * @param file the path to the presentation file.
     * @throws Exception if something goes wrong creating the presentation.
     */
    public OOPresentation(String file) throws Exception {
        if(!init) {
            throw new IllegalStateException("I'm not initialised yet! init() needs to be called before creating presentations.");
        }
        File sourceFile = new File(file);
        StringBuilder sURL = new StringBuilder("file:///");
        sURL.append(sourceFile.getCanonicalPath().replace('\\', '/'));
        PropertyValue[] props = new PropertyValue[1];
        props[0] = new PropertyValue();
        props[0].Name = "Silent";
        props[0].Value = true;

        doc = Helper.createDocument(xOfficeContext, sURL.toString(), "_blank", 0, props);
        XModel xModel = UnoRuntime.queryInterface(XModel.class, doc);
        xModel.getCurrentController().getFrame().getContainerWindow().setVisible(false);
        XPresentationSupplier xPresSupplier = UnoRuntime.queryInterface(XPresentationSupplier.class, doc);
        XPresentation xPresentation_ = xPresSupplier.getPresentation();
        xPresentation = UnoRuntime.queryInterface(XPresentation2.class, xPresentation_);

        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                checkDisposed();
            }
        });
    }

    /**
     * Start the presentation, displaying it in a full screen window.
     * @param display the 0 based index of the display to display the presentation on.
     */
    public void start(int display) {
        display++; //Openoffice requires base 1, we want base 0.
        try {
            xPresentation.setPropertyValue("Display", display);
            xPresentation.setPropertyValue("IsAutomatic", true);
            xPresentation.setPropertyValue("IsAlwaysOnTop", true);
        }
        catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException ex) {
            LOGGER.log(Level.SEVERE, "Error setting presentation properties", ex);
        }
        xPresentation.start();
        while(controller == null) { //Block until we get a controller.
            controller = xPresentation.getController();
            Utils.sleep(50);
        }
    }

    /**
     * Determine if this presentation is running.
     * @return true if its running, false otherwise.
     */
    public boolean isRunning() {
        if(controller != null) {
            return controller.isRunning();
        }
        else {
            return false;
        }
    }

    /**
     * Stop the presentation if it's running.
     */
    public void stop() {
        if(controller != null) {
            xPresentation.end();
            controller.deactivate();
            controller = null;
        }
    }

    /**
     * Advance forward one step. This will involve either advancing to the next animation
     * in the slide, or advancing to the next slide (depending on the presentation.)
     */
    public void goForward() {
        if(controller != null) {
            controller.gotoNextEffect();
        }
    }

    /**
     * Go backwards one step.
     */
    public void goBack() {
        if(controller != null) {
            controller.gotoPreviousEffect();
        }
    }

    /**
     * Navigate directly to the slide at the given index.
     * @param index the index of the slide to navigate to.
     */
    public void gotoSlide(int index) {
        if(controller != null) {
            controller.gotoSlideIndex(index);
        }
    }

    /**
     * Clear up this presentation, releasing all the resources associated with it (all the underlying OO library objects.) This must be called before this presentation is eligible for GC to prevent memory leaks. In the event that it isn't called before it's garbage collected, a warning will be printed since this should be classed as a bug.
     */
    public void dispose() {
        if(!disposed) {
            if(controller != null && controller.isActive()) {
                controller.deactivate();
            }
            if(xPresentation != null) {
                xPresentation.end();
            }
            if(doc != null) {
                XCloseable xcloseable = UnoRuntime.queryInterface(XCloseable.class, doc);
                try {
                    xcloseable.close();
                }
                catch (SQLException ex) {
                    LOGGER.log(Level.WARNING, "Error occured when closing presentation", ex);
                }
                doc.dispose();
            }
            disposed = true;
        }
    }

    /**
     * If the object hasn't been disposed, clean it up at this point and display a warning.
     * @throws Throwable if something goes wrong in finalisation.
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        checkDisposed();
    }

    /**
     * If the object hasn't been disposed, clean it up at this point and display a warning.
     */
    private void checkDisposed() {
        if(!disposed) {
            LOGGER.log(Level.WARNING, "BUG: Presentation was not correctly disposed!");
            dispose();
        }
    }

    /**
     * Helper methods for doing openoffice specific stuff.
     */
    private static class Helper {

        /**
         * Connect to an office, if no office is running a new instance is
         * started. A new connection is established and the service manger from
         * the running offic eis returned.
         *
         * @param path the path to the openoffice install.
         */
        private static XComponentContext connect(String path) throws BootstrapException {
            com.sun.star.uno.XComponentContext xOfficeContext = ooo.connector.BootstrapSocketConnector.bootstrap(path);
            return xOfficeContext;
        }

        /**
         * Creates and instantiates a new document
         * @throws Exception if something goes wrong creating the document.
         */
        private static XComponent createDocument(XComponentContext xOfficeContext, String sURL, String sTargetFrame, int nSearchFlags, PropertyValue[] aArgs) throws Exception {
            XComponentLoader aLoader = UnoRuntime.queryInterface(XComponentLoader.class, xOfficeContext.getServiceManager().createInstanceWithContext("com.sun.star.frame.Desktop", xOfficeContext));
            XComponent xComponent = UnoRuntime.queryInterface(XComponent.class, aLoader.loadComponentFromURL(sURL, sTargetFrame, nSearchFlags, aArgs));

            if(xComponent == null) {
                throw new Exception("Could not create document: " + sURL);
            }
            return xComponent;
        }
    }
}
