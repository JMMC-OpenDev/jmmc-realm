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

let $jar-name:="exist-security-jmmc.jar"
let $jar-present:= file:exists(system:get-exist-home()||"/lib/extensions/"||$jar-name)
let $security-config-set := exists($app:security-config//realm[@id="JMMC"])

return if ($security-config-set and $jar-present) then
        "All is fine : security config is set and jar is present."
    else if($jar-present) then
        (
            "Jar present."
            , app:install-jmmc-realm()
        )
    else if($security-config-set) then
        "Oups: security config set but jar is not present"
    else 
        "coucou"

};

declare function app:install-jmmc-realm() {
        try {
            update insert $app:jmmc-realm into $app:security-config/*,
            "JMMC realm just appent to security manager, please reboot existdb"
        } catch * {
            "JMMC realm failed to be appent to security manager, "||$err:description
        }
            
            
};