xquery version "3.0";

module namespace app="http://exist.jmmc.fr/jmmc-realm/templates";

import module namespace templates="http://exist-db.org/xquery/templates" ;
import module namespace config="http://exist.jmmc.fr/jmmc-realm/config" at "config.xqm";

declare variable $app:security-config := doc("/db/system/security/config.xml");

declare variable $app:jmmc-realm := <realm id="JMMC" xmlns="http://exist-db.org/Configuration">
       <url>https://apps.jmmc.fr/account/manage.php</url>
    </realm>;
    
(:~
 : Show the status of security managers.
 : 
 : @param $node the HTML node with the attribute which triggered this call
 : @param $model a map containing arbitrary data - used to pass information between template calls
 :)
declare function app:status($node as node(), $model as map(*)) {
  " Everything seems fine"
  (: More tests here :)
};
