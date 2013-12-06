/*
 * Javascript library to create knowlegeable slickGrids with token info in them
 */

function AppSlickGrid(path, urlEncodedPath) {

    var grid;
    var dv;

    var rowCounter = 1;
    function addNewRow() {
        var item = {'id': 'new_' + rowCounter};
        dv.addItem(item);
    }

    function deleteButtonFormatter(row, cell, value, columnDef, dataContext) {

        var button = "<input class='ApplicationDelete' type='button' id='" + dataContext.id + "' value='delete'>";
        return button;
    }

    function appFormatter(row, cell, value, columnDef, dataContext) {
        if(typeof value == 'undefined') {
            return ''
        } else {
            var pathname = dataContext.name;
            var name = pathname.substring(path.length);
            var link = "<a href='/application/" + encodeURIComponent(pathname) + "'>" + name + "</a>";
            return link;
        }
    }

    var columns = [
        {id: 'application', name: 'Application', field: 'name', width: 800, editor: Slick.Editors.Text, sortable: true, formatter: appFormatter},
        {id: 'description', name: 'Description', field: 'description', width: 200, editor: Slick.Editors.Text},
        {id: 'delete', name: 'Delete', field: 'delete', width: 200, formatter: deleteButtonFormatter}];

    var options = {
        editable: true,
        enableAddRow: false,
        enableCellNavigation: true,
        autoEdit: false
    };

    var loadingIndicator = null;


    function reloadTable() {
        $.ajax({
            url: '/api/applications/' + urlEncodedPath,
            type: 'GET',
            success: function(data) {
                for (i = 0; i < data.length; i++) {
                    data[i]['id'] = data[i]['name'];
                }
                dv.beginUpdate();
                dv.setItems(data);
                dv.endUpdate();
                grid.invalidate();
                grid.render();
            }});
    }

    function submitApps(data) {
        console.log("Sumitting applications" + data);
        $.ajax({type: 'PUT',
                url: '/api/applications',
                //          contentType: 'application/json; charset=UTF-8',
                contentType: 'text/plain',
                dataType: 'json',
                data: data,
                success: function(data) {
                    if(data.status != 'success') {
                        alert('Error making changes:\\n' + data.message);
                    } else {
                        reloadTable();
                    }},
                error: function(request, status, message) {
                    alert('Error ' + status + ' making changes: ' + message);
                }
               });
    }


    $(function () {
        dv = Slick.Data.DataView();
        var gridSorter = function(columnField, isAsc, grid, gridData) {
            var sign = isAsc ? 1 : -1;
            var field = columnField
            gridData.sort(function (dataRow1, dataRow2) {
                var value1 = dataRow1[field], value2 = dataRow2[field];
                var result = (value1 == value2) ?  0 :
                    ((value1 > value2 ? 1 : -1)) * sign;
                return result;
            });
            grid.invalidate();
            grid.render();
        }

        grid = new Slick.Grid('#appdiv', dv, columns, options);

        gridSorter('app', true, grid, dv);

        grid.setSortColumn('app', true);

        grid.autosizeColumns();

        grid.onAddNewRow.subscribe(function (e, args) {
            var item = args.item;
            grid.invalidateRow(dv.length);
            dv.addItem(item);
            grid.updateRowCount();
            grid.render();
        });

        reloadTable();

        grid.onSort.subscribe(function(e, args) {
            gridSorter(args.sortCol.field, args.sortAsc, grid, dv);
        });

        dv.onRowCountChanged.subscribe(function(e, args) {
            grid.updateRowCount();
            grid.render();
        });

        dv.onRowsChanged.subscribe(function (e, args) {
            grid.invalidateRows(args.rows);
            grid.render();
        });

        grid.onCellChange.subscribe(function (e, args) {
            var row = dv.getItem(args.row);
            var columns = grid.getColumns();
            row['changed'] = true;
        });

        $('.ApplicationDelete').live('click', function() {
            var me = $(this);
            var id = me.attr('id');
            var row = dv.getItemById(id);
            var name = row['name'];
            var environment = row['environment'];
            var delconf = confirm('Do you really want to delete the '
                                  + name + 'application?');
            if (delconf) {
                $.ajax({ url: '/api/applications/' + urlEncodedPath + name,
                         type: 'DELETE',
                         success: function(data) {
                             if(data.status != 'success') {
                                 alert('Error deleting ' + name + ':\n'
                                       + data.message)
                             }
                             reloadTable();
                         },
                         error: function(req, status, message) {
                             alert('Error deleting ' +  name);
                             reloadTable();;
                         }
                       });
            }
        });

        $('.AppSubmitChanges').live('click', function() {
            var data = dv.getItems();
            var result = [];
            for(i = 0; i < data.length; i++) {
                if (data[i]['changed']) {
                    data[i]['path'] = path;
                    result.push(data[i]);
                }
            }
            submitApps(JSON.stringify(result));
        });

        $('.AppResetChanges').live('click', function() {
            reloadTable();
        });

        $('.AppAddNewRow').live('click', function() {
            addNewRow();
        });
    });
}
