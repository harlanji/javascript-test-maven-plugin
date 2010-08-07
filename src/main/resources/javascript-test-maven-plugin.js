
function jtmp_locate_scripts() {
  var scripts=[], tags=document.getElementsByTagName('script');
  for (var i=0,tag; tag=tags[i]; i++ ) {
      if (tag.getAttribute('language') && tag.getAttribute('language').toLowerCase().indexOf('javascript') != 0)
          continue;
      if (tag.getAttribute('type') && tag.getAttribute('type').toLowerCase().indexOf('javascript') < 0)
          continue;
      if (tag.getAttribute('src'))
         scripts.push( 'file:' + tag.getAttribute('src') );
      else
         scripts.push( tag.innerHTML );
  }
  return scripts;
}

function jtmp_locate_css() {
  return jQuery.map(jQuery("link[rel='stylesheet']"),function(l){return jQuery(l).attr('href');});
}

function jtmp_failure_messages() {
  return jQuery.map(jQuery('.it'),function(l){
    return {test:jQuery('h2',l).html(),error:jQuery('.error',l).html()}
   });
}