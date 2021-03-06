/*
 * Javascript library to create knowlegeable slickGrids with token info in them
 */

function AppSlickGrid(path, urlEncodedPath, prefix, delimiter) {

    var grid;
    var dv;
    var currentSortField = 'name';
    var isAsc = true;

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
        if(value == undefined) {
            return '';
        } else if (value.indexOf(path) == 0) {
            var name = value.substring(path.length);
            var link = "<a href='" + prefix + "/application/" + encodeURIComponent(value) + "'>" + name + "</a>";
            return link;
        } else {
            return value;
        }
    }

    var columns = [
        {id: 'application', name: 'Application', field: 'name', editor: Slick.Editors.Text, sortable: true, formatter: appFormatter},
        {id: 'description', name: 'Description', field: 'description', editor: Slick.Editors.Text},
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
            url: prefix + '/api/applications/' + urlEncodedPath + '?sort-col='
                + currentSortField + '&sort-dir=' + (isAsc ? 'asc' : 'desc'),
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
                url: prefix + '/api/applications',
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
        grid = new Slick.Grid('#appdiv', dv, columns, options);

        grid.setSortColumn('application', true);

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
            currentSortField = args.sortCol.field;
            isAsc = args.sortAsc
            reloadTable();
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
                $.ajax({ url: prefix + '/api/applications/' + encodeURIComponent(name.replace('/', delimiter)),
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

    function resize() {
        var width = $(window).width() - 25;
        $("#appdiv").width(width);
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
