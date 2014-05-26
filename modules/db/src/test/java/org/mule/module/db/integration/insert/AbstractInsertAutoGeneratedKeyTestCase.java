/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.db.integration.insert;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import org.mule.api.MuleMessage;
import org.mule.api.client.LocalMuleClient;
import org.mule.module.db.integration.AbstractDbIntegrationTestCase;
import org.mule.module.db.integration.model.AbstractTestDatabase;
import org.mule.module.db.integration.TestDbConfig;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runners.Parameterized;

public abstract class AbstractInsertAutoGeneratedKeyTestCase extends AbstractDbIntegrationTestCase
{

    public AbstractInsertAutoGeneratedKeyTestCase(String dataSourceConfigResource, AbstractTestDatabase testDatabase)
    {
        super(dataSourceConfigResource, testDatabase);
    }

    @Parameterized.Parameters
    public static List<Object[]> parameters()
    {
        return TestDbConfig.getResources();
    }

    @Test
    public void returnsAutoGeneratedKeys() throws Exception
    {
        LocalMuleClient client = muleContext.getClient();
        MuleMessage response = client.send("vm://insertWithAutoGeneratedKeys", TEST_MESSAGE, null);

        assertThat(response.getPayload(), is(List.class));
        List generatedKeys = (List) response.getPayload();
        assertThat(generatedKeys.size(), equalTo(1));
        assertThat(generatedKeys.get(0), is(Map.class));
        assertThat(((Map) generatedKeys.get(0)).values().toArray()[0], is(getIdFieldJavaClass()));
    }

    protected Class getIdFieldJavaClass()
    {
        return testDatabase.getIdFieldJavaClass();
    }
}