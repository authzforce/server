/**
 * Copyright (C) 2012-2017 Thales Services SAS.
 *
 * This file is part of AuthZForce CE.
 *
 * AuthZForce CE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthZForce CE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthZForce CE.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.web.test.pep.cxf;

import java.net.URL;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.testutil.common.AbstractBusTestServerBase;

public class PdpServer extends AbstractBusTestServerBase {

    public PdpServer() {

    }

    protected void run()  {
        URL busFile = PdpServer.class.getResource("cxf-pdp.xml");
        Bus busLocal = new SpringBusFactory().createBus(busFile);
        BusFactory.setDefaultBus(busLocal);
        setBus(busLocal);

        try {
            new PdpServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
