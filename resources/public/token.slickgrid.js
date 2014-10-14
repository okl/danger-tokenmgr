/*
 * This library makes slick grid tables for token types
 * @author Eric Sayle
 */

function TokenSlickGrid(path, urlEncodedPath, prefix) {

    var grid;
    var dv;

    var rowCounter = 1;
    function addNewRow() {
        var item = {'id': 'new_' + rowCounter};
        dv.addItem(item);
    }

    function deleteButtonFormatter(row, cell, value, columnDef, dataContext) {
        if(dataContext.source == path) {
            var button = "<input class='TokenDelete' type='button' id='" + dataContext.id + "' value='delete'>";
            return button;
        } else {
            return "";
        }
    }

    function sourceFormatter(row, cell, value, columnDef, dataContext) {
        if(typeof value == 'undefined') {
            return ''
        } else if(value == '') {
            return '/';
        } else {
            return value;
        }
    }

    var columns = [
        {id: 'token', name: 'Token', field: 'name', editor: Slick.Editors.Text, sortable: true},
        {id: 'description', name: 'Description', field: 'description', editor: Slick.Editors.Text},
        {id: 'environment', name: 'Environment', field: 'environment', sortable: true, editor: Slick.Editors.Text},
        {id: 'value', name: 'Value', field: 'value', editor: Slick.Editors.Text},
        {id: 'source', name: 'Source', field: 'source', sortable: true, formatter: sourceFormatter},
        {id: 'delete', name: 'Delete', field: 'delete', formatter: deleteButtonFormatter, maxWidth: 80}];

    var options = {
        editable: true,
        enableAddRow: false,
        enableCellNavigation: true,
        autoEdit: false
    };

    var loadingIndicator = null;


    function reloadTable() {
        $.ajax({
            url: prefix + '/api/tokens/' + urlEncodedPath,
            type: 'GET',
            success: function(data) {
                for (i = 0; i < data.length; i++) {
                    data[i]['id'] = data[i]['name'] + ':' + data[i]['environment'];
                }
                dv.beginUpdate();
                dv.setItems(data);
                dv.endUpdate();
                grid.invalidate();
                grid.render();
            }});
    }

    function submitTokens(data) {
        console.log("Sumitting tokens" + data);
        $.ajax({type: 'PUT',
                url: prefix + '/api/tokens',
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

        grid = new Slick.Grid('#tokendiv', dv, columns, options);

        gridSorter('token', true, grid, dv);

        grid.setSortColumn('token', true);

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

        $('.TokenDelete').live('click', function() {
            var me = $(this);
            var id = me.attr('id');
            var row = dv.getItemById(id);
            var name = row['name'];
            var environment = row['environment'];
            var delconf = confirm('Do you really want to delete the ' + environment +
                                  ' value for the ' + name + 'token?');
            if (delconf) {
                var pathname;
                if( urlEncodedPath == '') {
                    pathname = name;
                } else {
                    pathname = urlEncodedPath + '%2f' + name;
                }
                $.ajax({ url: prefix + '/api/tokens/' + pathname + '/' + environment,
                         type: 'DELETE',
                         success: function(data) {
                             if(data.status != 'success') {
                                 alert('Error deleting ' + name + ':\\n' + data.message)
                             }
                             document.location.reload(true);
                         },
                         error: function(req, status, message) {
                             alert('Error deleting ' +  name);
                             document.location.reload(true);
                         }
                       });
            }
        });

        $('.TokenSubmitChanges').live('click', function() {
            var data = dv.getItems();
            var result = [];
            for(i = 0; i < data.length; i++) {
                if (data[i]['changed']) {
                    data[i]['path'] = path;
                    if(data[i]['description'] == null ){
                        data[i]['description'] = "";
                    }
                    if(data[i]['value'] == null) {
                        data[i]['value'] = "";
                    }
                    result.push(data[i]);
                }
            }
            submitTokens(JSON.stringify(result));
        });

        $('.TokenResetChanges').live('click', function() {
            reloadTable();
        });

        $('.TokenAddNewRow').live('click', function() {
            addNewRow();
        });
    });

 function resize() {
     var width = $(window).width() - 25;
     $("#tokendiv").width(width);
     grid.resizeCanvas();
     grid.autosizeColumns();
 }


if(window.attachEvent) {
    window.attachEvent('onresize', function() {
        resize();
    });
}
else if(window.addEventListener) {
    window.addEventListener('resize', function() {
        resize();
    }, true);
}



    return grid;
}
