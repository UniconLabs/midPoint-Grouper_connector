# midPoint-Grouper_connector
This is a connector that can read groups from a Grouper instance using REST calls.
Currently it supports these searches only:
- fetching all groups,
- fetching a group by name,
- fetching a group by UUID.

When fetching a group, a client can choose whether to get basic group data only (name, UUID, extension) or whether
to obtain a list of group members as well. 

Besides `search` operation the following ones are supported:
- `schema`
- `test`

This connector was tested with Grouper 2.4.