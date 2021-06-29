# midPoint-Grouper_connector
This is a connector that can read groups from a Grouper instance using REST calls.
Currently, it supports these searches only:
- fetching all groups,
- fetching a group by name,
- fetching a group by UUID.

When fetching a group, a client can choose whether to get basic group data only (name, UUID, extension) or whether
to obtain a list of group members as well. 

Besides `search` operation the following ones are supported:
- `schema`
- `test`

This connector was tested with Grouper 2.5.

//TODO: Document baseStem, sourceId, include/exclude Group, and Group Attribute Map params and how they interact based on Grouper WS 


It's strongly recommended to add timeouts to your midPoint resource!

```xml
        <icfc:timeouts>
            <icfc:create>180000</icfc:create>
            <icfc:get>180000</icfc:get>
            <icfc:update>180000</icfc:update>
            <icfc:delete>180000</icfc:delete>
            <icfc:test>60000</icfc:test>
            <icfc:scriptOnConnector>180000</icfc:scriptOnConnector>
            <icfc:scriptOnResource>180000</icfc:scriptOnResource>
            <icfc:authentication>60000</icfc:authentication>
            <icfc:search>180000</icfc:search>
            <icfc:validate>180000</icfc:validate>
            <icfc:sync>180000</icfc:sync>
            <icfc:schema>60000</icfc:schema>
        </icfc:timeouts>
```