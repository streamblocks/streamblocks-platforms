/**
 * command_option_base.cpp - 
 * @author: Jonathan Beard
 * @version: Tue May 21 13:56:51 2013
 */
#include <sstream>
#include <string>
#include <cstring>
#include <iostream>
#include <fstream>
#include <iomanip>
#include <cassert>

#include "command_option_base.hpp"

#ifndef TERM_WIDTH
#define TERM_WIDTH 65
#endif

#ifndef FLAG_WIDTH
#define FLAG_WIDTH 30
#endif

OptionBase::OptionBase( const std::string Flag,
                        const std::string Description,
                        bool  isMandatory,
                        bool  isBool  ) : 
                                       set( false ),
                                       flag( Flag ),
                                       description( Description ),
                                       isbool( isBool ),
                                       mandatory( isMandatory )
{
   /* nothing to do here */
}

std::string 
OptionBase::toString( const std::string defaultValue )
{
     const size_t total_width( TERM_WIDTH );
     const size_t flag_width( FLAG_WIDTH );
     std::stringstream s;
     s.flags(std::ios::left);
     s.width(flag_width);
     s << get_flag();
     s.flags(std::ios::right);
     
     /* format the description + default properly */
     const size_t description_width( total_width - flag_width );
     std::stringstream desc;
desc << "// " << description << ", default: " << defaultValue << 
   ", mandatory: " << (is_mandatory() ? "true" : "false" );
     const std::string desc_str( desc.str() );
     if(desc_str.length() > description_width){
        /* width needs to be shortened and made multi-line */
char *space_buffer = (char*) malloc(sizeof(char) * flag_width + 4);
        if(space_buffer == (char*)NULL){ 
           perror("Failed to initialize space buffer!!");
           exit( EXIT_FAILURE );
        }
        memset( space_buffer, 0x20 /* space */, sizeof(char) * 
                                                  (flag_width + 4 ) );
        space_buffer[ flag_width + 3 ] = '\0'; /* NULL term */ 
        size_t char_count(1);
        for( std::istringstream iss( desc_str ); iss.good();)
        {
           std::string curr_token;
           iss >> curr_token;
           char_count += ( curr_token.length() + 1 /* space */ );
           if( char_count > description_width ){
              s << "\n";
              s << space_buffer;
              char_count = 1;
           }
           s << curr_token << " ";
        }
        free( space_buffer );
     }else{
        /* it fits */
        s << desc_str;
     }
    return( s.str() );
}


std::string& 
OptionBase::get_flag()
{
   return( flag );
}

std::string&
OptionBase::get_description()
{
   return( description );
}



bool 
OptionBase::is_set()
{
   return( (this)->set );
}

bool 
OptionBase::is_mandatory()
{
   return( (this)->mandatory );
}

bool
OptionBase::is_bool()
{
   return( (this)->isbool );
}
