function download(filename, text) {
    var pom = document.createElement('a');
    pom.setAttribute('href', 'data:text/plain;charset=utf-8,' + encodeURIComponent(text));
    pom.setAttribute('download', filename);
    pom.click();
}

// Adds button actions for Import/Export
$(document).ready(function() {
	$('#input-action-upload').click(function(e) {
		e.preventDefault();
		var formData = new FormData();
		formData.append('file',$('#input-file-upload')[0].files[0]);

		var xhr = new XMLHttpRequest;
		xhr.open('POST', '/api/file/test', false);
		xhr.responseType = 'file';
		xhr.onload = function(e) {
			if (this.status == 200) {
				download('tokenexport.json', this.response);
			}
		}
		xhr.send(formData);
	});

	$('#action-import').click(function(e) {
		e.preventDefault();
		var xhr = new XMLHttpRequest;
		xhr.open('POST','/api/file/import',false);
		xhr.send();
	});
	$('#action-export').click(function(e) {
		e.preventDefault();
		var xhr = new XMLHttpRequest;
		xhr.open('POST','/api/file/export',false);
		xhr.send();
	});
});
